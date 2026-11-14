package coursecraft.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import coursecraft.llm.GroqClient;
import coursecraft.llm.ToolDefs;
import coursecraft.service.ToolException;
import coursecraft.service.ToolService;
import coursecraft.util.Config;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * POST /api/agent   goal=...   [course_id=...]
 *
 * plan    : send the goal + tool definitions to the LLM, which picks a tool
 * act     : run that tool through ToolService, as the LOGGED-IN student (userId comes from the
 *           session, never from the model), so the agent can do no more than the student can
 * observe : write the call and its result to agent_log and return it to the page
 *
 * AGENT_MAX_STEPS (default 1) is the number of plan-act-observe cycles: 1 for the milestone,
 * up to 8 lets the model chain tools (e.g. read quiz results, then save a plan).
 */
@WebServlet("/api/agent")
public class AgentServlet extends BaseApiServlet {

    private static final int MAX_GOAL_LENGTH = 1000;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");            // must run before the first getParameter
        Integer userId = requireUser(request, response);
        if (userId == null) {
            return;
        }

        String goal = request.getParameter("goal");
        goal = goal == null ? "" : goal.trim();
        if (goal.isEmpty() || goal.length() > MAX_GOAL_LENGTH) {
            send(response, 400, error("goal must be 1 to " + MAX_GOAL_LENGTH + " characters"));
            return;
        }
        Integer selectedCourse = null;
        String courseParam = request.getParameter("course_id");
        if (courseParam != null && !courseParam.trim().isEmpty()) {
            selectedCourse = positiveInt(courseParam);
            if (selectedCourse == null) {
                send(response, 400, error("course_id must be a positive integer"));
                return;
            }
        }

        int status = 200;
        JsonObject out;
        try {
            if (selectedCourse != null) {
                ToolService.assertOwnsCourse(userId, selectedCourse);       // 403 if not theirs
            }
            if (!GroqClient.hasApiKey()) {
                throw new ToolException(500, "LLM_API_KEY is not configured on the server");
            }
            out = runAgent(userId, goal, selectedCourse);
        } catch (ToolException e) {
            status = e.getStatus();
            out = error(e.getMessage());
        } catch (IOException e) {                         // LLM call failed (message is user-safe)
            System.err.println("Agent LLM call failed: " + e.getMessage());
            status = 502;
            out = error(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = 500;
            out = error("The request was interrupted");
        }
        send(response, status, out);
    }

    private static JsonObject runAgent(int userId, String goal, Integer selectedCourse)
            throws ToolException, IOException, InterruptedException {
        int maxSteps = maxSteps();
        JsonArray tools = ToolDefs.all();
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt(userId, selectedCourse)));
        messages.add(message("user", goal));

        JsonArray steps = new JsonArray();
        String answer = null;
        String llmError = null;

        for (int step = 1; step <= maxSteps; step++) {
            // ---- PLAN ----
            JsonObject reply;
            try {
                reply = GroqClient.chat(messages, tools);
            } catch (IOException e) {
                if (steps.size() == 0) {
                    throw e;                              // nothing happened yet: report the failure
                }
                llmError = e.getMessage();                // keep the steps that already ran
                break;
            }
            JsonObject call = firstToolCall(reply);
            if (call == null) {                           // the model answered in plain text
                answer = text(reply);
                break;
            }
            JsonObject fn = call.getAsJsonObject("function");
            String name = fn.has("name") && !fn.get("name").isJsonNull() ? fn.get("name").getAsString() : "";
            String argsText = fn.has("arguments") && !fn.get("arguments").isJsonNull()
                    ? fn.get("arguments").getAsString() : "{}";
            String callId = call.has("id") && !call.get("id").isJsonNull()
                    ? call.get("id").getAsString() : "call_" + step;
            JsonObject args = parseArgs(argsText);

            // ---- ACT (a tool failure is just another observation for the model) ----
            JsonObject result;
            boolean ok;
            try {
                result = ToolService.executeTool(userId, name, args);
                ok = true;
            } catch (ToolException e) {
                result = error(e.getMessage());
                ok = false;
            }

            // ---- OBSERVE ----
            ToolService.logCall(userId, name, args.toString(), result.toString(), ok);
            JsonObject s = new JsonObject();
            s.addProperty("step", step);
            s.addProperty("tool", name);
            s.add("arguments", args);
            s.addProperty("success", ok);
            s.add("result", result);
            steps.add(s);

            // feed the observation back so the model can continue (only used when maxSteps > 1)
            messages.add(assistantToolCall(callId, name, argsText));
            messages.add(toolResult(callId, result.toString()));
        }

        JsonObject out = new JsonObject();
        out.addProperty("success", true);
        out.addProperty("goal", goal);
        out.addProperty("max_steps", maxSteps);
        out.add("steps", steps);
        if (answer != null) {
            out.addProperty("answer", answer);
        }
        if (llmError != null) {
            out.addProperty("llm_error", llmError);
        }
        out.addProperty("stopped", answer != null ? "completed" : (llmError != null ? "llm_error" : "step_limit"));
        return out;
    }

    // ---------- prompt + message helpers ----------

    /** Gives the model the student's REAL course ids so it never has to guess them. */
    private static String systemPrompt(int userId, Integer selectedCourse) throws ToolException {
        JsonArray courses = ToolService.listCourses(userId).getAsJsonArray("courses");
        StringBuilder list = new StringBuilder();
        for (JsonElement el : courses) {
            JsonObject c = el.getAsJsonObject();
            list.append("course_id=").append(c.get("id").getAsInt())
                .append(" (").append(c.get("name").getAsString()).append("); ");
        }
        StringBuilder sb = new StringBuilder("You are CourseCraft's study assistant for a college student. ");
        sb.append("The student's courses are: ").append(list.length() == 0 ? "(none)" : list).append(" ");
        if (selectedCourse != null) {
            sb.append("The student currently has course_id=").append(selectedCourse)
              .append(" selected on the page; use it when they say \"this course\". ");
        }
        sb.append("Use the tools to look up the student's real notes, course materials and quiz results ")
          .append("instead of guessing. Always pass a course_id from the list above. ")
          .append("Call save_study_plan only when the student asks you to create or save a study plan, ")
          .append("and put the complete plan text in plan_text.");
        return sb.toString();
    }

    private static int maxSteps() {
        try {
            return Math.max(1, Math.min(8, Integer.parseInt(Config.get("agent.max.steps", "1"))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** The first tool call in the model's reply, or null if it replied with text only. */
    private static JsonObject firstToolCall(JsonObject reply) {
        JsonElement tc = reply.get("tool_calls");
        if (tc == null || !tc.isJsonArray() || tc.getAsJsonArray().size() == 0) {
            return null;
        }
        JsonObject call = tc.getAsJsonArray().get(0).getAsJsonObject();
        return call.has("function") && call.get("function").isJsonObject() ? call : null;
    }

    private static String text(JsonObject reply) {
        JsonElement c = reply.get("content");
        return (c == null || c.isJsonNull()) ? "" : c.getAsString();
    }

    /** Malformed arguments become an empty object, so the tool reports a normal validation error. */
    private static JsonObject parseArgs(String argsText) {
        try {
            JsonElement e = JsonParser.parseString(argsText);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static JsonObject assistantToolCall(String id, String name, String argsText) {
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("arguments", argsText);
        JsonObject call = new JsonObject();
        call.addProperty("id", id);
        call.addProperty("type", "function");
        call.add("function", fn);
        JsonArray calls = new JsonArray();
        calls.add(call);
        JsonObject m = new JsonObject();
        m.addProperty("role", "assistant");
        m.addProperty("content", "");
        m.add("tool_calls", calls);
        return m;
    }

    private static JsonObject toolResult(String id, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", "tool");
        m.addProperty("tool_call_id", id);
        m.addProperty("content", content);
        return m;
    }
}
