package coursecraft.web;

import coursecraft.service.ToolException;
import coursecraft.service.ToolService;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** GET /api/courses: the logged-in student's courses (database -> servlet -> page). */
@WebServlet("/api/courses")
public class CoursesServlet extends BaseApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer userId = requireUser(request, response);
        if (userId == null) {
            return;
        }
        try {
            send(response, 200, ToolService.listCourses(userId));
        } catch (ToolException e) {
            send(response, e.getStatus(), error(e.getMessage()));
        }
    }
}
