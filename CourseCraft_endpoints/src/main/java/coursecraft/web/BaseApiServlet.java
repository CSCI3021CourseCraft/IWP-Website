package coursecraft.web;

import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/** Shared helpers: session check, JSON responses, input parsing. */
public abstract class BaseApiServlet extends HttpServlet {

    /** Returns the logged-in user's id, or sends 401 JSON and returns null. */
    protected Integer requireUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);        // false: never create a session here
        Object id = (session == null) ? null : session.getAttribute("userId");
        if (!(id instanceof Integer)) {
            send(resp, 401, error("Not logged in"));
            return null;
        }
        return (Integer) id;
    }

    protected static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("success", false);
        o.addProperty("error", message);
        return o;
    }

    protected static void send(HttpServletResponse resp, int status, JsonObject body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body.toString());
    }

    /** Parses a positive integer, or returns null if missing or invalid. */
    protected static Integer positiveInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
