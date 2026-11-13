package coursecraft.web;

import coursecraft.service.ToolException;
import coursecraft.service.ToolService;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Tool: get_course_notes.  GET /api/tools/get_course_notes?course_id=1 */
@WebServlet("/api/tools/get_course_notes")
public class NotesToolServlet extends BaseApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer userId = requireUser(request, response);
        if (userId == null) {
            return;
        }
        Integer courseId = positiveInt(request.getParameter("course_id"));
        if (courseId == null) {
            send(response, 400, error("course_id must be a positive integer"));
            return;
        }
        try {
            send(response, 200, ToolService.getCourseNotes(userId, courseId));
        } catch (ToolException e) {
            send(response, e.getStatus(), error(e.getMessage()));
        }
    }
}
