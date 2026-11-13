package coursecraft.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import coursecraft.util.DbUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The agent's tools. The REST servlets AND the AgentServlet both call these methods,
 * so the agent can never do more than a logged in student can do.
 * Every method takes the userId from the SESSION, never from the model or the request.
 */
public class ToolService {

    private static final Set<String> ITEM_TYPES =
            new HashSet<>(Arrays.asList("assignment", "module", "quiz", "file"));
    private static final int MAX_PLAN_LENGTH = 5000;

    // ---------- helpers ----------
    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("success", true);
        return o;
    }

    private static ToolException dbError(SQLException e) {
        e.printStackTrace();// details go to the server log only
        return new ToolException(500, "Database error");
    }

    /** Throws 403 unless the course exists and belongs to this user. */
    private static void requireOwnedCourse(Connection conn, int userId, int courseId)
            throws SQLException, ToolException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM courses WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, courseId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ToolException(403, "Course not found or not yours");
                }
            }
        }
    }

    public static void assertOwnsCourse(int userId, int courseId) throws ToolException {
        Connection conn = null;
        try {
            conn = DbUtil.getConnection();
            requireOwnedCourse(conn, userId, courseId);
        } catch (SQLException e) {
            throw dbError(e);
        } finally {
            DbUtil.close(conn);
        }
    }

    // ---------- tool 1: read ----------
    public static JsonObject getCourseNotes(int userId, int courseId) throws ToolException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            requireOwnedCourse(conn, userId, courseId);
            ps = conn.prepareStatement(
                    "SELECT id, title, body, updated_at FROM notes WHERE course_id = ? ORDER BY updated_at DESC");
            ps.setInt(1, courseId);
            rs = ps.executeQuery();
            JsonArray notes = new JsonArray();
            while (rs.next()) {
                JsonObject n = new JsonObject();
                n.addProperty("id", rs.getInt("id"));
                n.addProperty("title", rs.getString("title"));
                n.addProperty("body", rs.getString("body"));
                n.addProperty("updated_at", rs.getString("updated_at"));
                notes.add(n);
            }
            JsonObject out = ok();
            out.addProperty("course_id", courseId);
            out.add("notes", notes);
            return out;
        } catch (SQLException e) {
            throw dbError(e);
        } finally {
            DbUtil.close(rs, ps, conn);
        }
    }

    // ---------- tool 2: read ----------
    public static JsonObject getCourseMaterials(int userId, int courseId, String type) throws ToolException {
        boolean filter = type != null && !type.isEmpty();
        if (filter && !ITEM_TYPES.contains(type)) {
            throw new ToolException(400, "type must be one of: assignment, module, quiz, file");
        }
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            requireOwnedCourse(conn, userId, courseId);
            String sql = "SELECT id, type, title, due_date, body FROM canvas_items WHERE course_id = ?"
                    + (filter ? " AND type = ?" : "")
                    + " ORDER BY due_date IS NULL, due_date";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, courseId);
            if (filter) {
                ps.setString(2, type);
            }
            rs = ps.executeQuery();
            JsonArray items = new JsonArray();
            while (rs.next()) {
                JsonObject it = new JsonObject();
                it.addProperty("id", rs.getInt("id"));
                it.addProperty("type", rs.getString("type"));
                it.addProperty("title", rs.getString("title"));
                it.addProperty("due_date", rs.getString("due_date"));
                it.addProperty("body", rs.getString("body"));
                items.add(it);
            }
            JsonObject out = ok();
            out.addProperty("course_id", courseId);
            out.add("items", items);
            return out;
        } catch (SQLException e) {
            throw dbError(e);
        } finally {
            DbUtil.close(rs, ps, conn);
        }
    }

    // ---------- tool 3: WRITE (changes application state) ----------
    public static JsonObject saveStudyPlan(int userId, int courseId, String planText) throws ToolException {
        if (planText == null || planText.trim().isEmpty()) {
            throw new ToolException(400, "plan_text is required");
        }
        if (planText.length() > MAX_PLAN_LENGTH) {
            throw new ToolException(400, "plan_text is too long (max " + MAX_PLAN_LENGTH + " characters)");
        }
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet keys = null;
        try {
            conn = DbUtil.getConnection();
            requireOwnedCourse(conn, userId, courseId);
            ps = conn.prepareStatement(
                    "INSERT INTO study_plans (course_id, plan_text) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, courseId);
            ps.setString(2, planText.trim());
            ps.executeUpdate();
            keys = ps.getGeneratedKeys();
            JsonObject out = ok();
            out.addProperty("course_id", courseId);
            if (keys.next()) {
                out.addProperty("plan_id", keys.getInt("id"));
            }
            return out;
        } catch (SQLException e) {
            throw dbError(e);
        } finally {
            DbUtil.close(keys, ps, conn);
        }
    }

    // ---------- courses list (for the page) ----------
    public static JsonObject listCourses(int userId) throws ToolException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement("SELECT id, name FROM courses WHERE user_id = ? ORDER BY name");
            ps.setInt(1, userId);
            rs = ps.executeQuery();
            JsonArray arr = new JsonArray();
            while (rs.next()) {
                JsonObject c = new JsonObject();
                c.addProperty("id", rs.getInt("id"));
                c.addProperty("name", rs.getString("name"));
                arr.add(c);
            }
            JsonObject out = ok();
            out.add("courses", arr);
            return out;
        } catch (SQLException e) {
            throw dbError(e);
        } finally {
            DbUtil.close(rs, ps, conn);
        }
    }

    // ---------- the agent's dispatcher ----------
    public static boolean isKnownTool(String name) {
        return "get_course_notes".equals(name) || "get_course_materials".equals(name)
                || "save_study_plan".equals(name);
    }

    /** Runs a tool the model asked for. Only whitelisted tools; userId always comes from the session. 
    public static JsonObject executeTool(int userId, String tool, JsonObject input) throws ToolException {
        if (!isKnownTool(tool)) {
            throw new ToolException(400, "Unknown tool: " + tool);
        }
        switch (tool) {
            case "get_course_notes":
                return getCourseNotes(userId, intArg(input, "course_id"));
            case "get_course_materials":
                return getCourseMaterials(userId, intArg(input, "course_id"), strArg(input, "type", false));
            default:
                return saveStudyPlan(userId, intArg(input, "course_id"), strArg(input, "plan_text", true));
        }
    }
    */

    private static int intArg(JsonObject input, String name) throws ToolException {
        JsonElement e = input.get(name);
        if (e == null || e.isJsonNull()) {
            throw new ToolException(400, name + " is required");
        }
        int v;
        try {
            v = e.getAsInt();
        } catch (RuntimeException ex) {
            throw new ToolException(400, name + " must be a positive integer");
        }
        if (v <= 0) {
            throw new ToolException(400, name + " must be a positive integer");
        }
        return v;
    }

    private static String strArg(JsonObject input, String name, boolean required) throws ToolException {
        JsonElement e = input.get(name);
        if (e == null || e.isJsonNull()) {
            if (required) {
                throw new ToolException(400, name + " is required");
            }
            return null;
        }
        try {
            return e.getAsString();
        } catch (RuntimeException ex) {
            throw new ToolException(400, name + " must be text");
        }
    }

    /** ---------- agent log ----------
    public static void logCall(int userId, String toolName, String input, String output) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(
                    "INSERT INTO agent_log (user_id, tool_name, input, output, success) VALUES (?, ?, ?, ?, ?)");
            ps.setInt(1, userId);
            ps.setString(2, toolName);
            ps.setString(3, input);
            ps.setString(4, output == null || output.length() <= 20000 ? output : output.substring(0, 20000));
            ps.setBoolean(5, output != null && output.contains("\"success\":true"));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DbUtil.close(ps, conn);
        }
    }
    */
}
