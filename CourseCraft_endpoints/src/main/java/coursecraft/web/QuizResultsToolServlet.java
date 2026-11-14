package coursecraft.web;

import coursecraft.service.ToolException;
import coursecraft.service.ToolService;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Tool: get_quiz_results.  GET /api/tools/get_quiz_results?course_id=1 */
@WebServlet("/api/tools/get_quiz_results")
public class QuizResultsToolServlet extends BaseApiServlet {

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
            send(response, 200, ToolService.getQuizResults(userId, courseId));
        } catch (ToolException e) {
            send(response, e.getStatus(), error(e.getMessage()));
        }
    }
}
