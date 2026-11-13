package coursecraft.web;

import coursecraft.service.ToolException;
import coursecraft.service.ToolService;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Tool: save_study_plan (changes data).  POST /api/tools/save_study_plan  course_id=1&plan_text=... */
@WebServlet("/api/tools/save_study_plan")
public class SavePlanToolServlet extends BaseApiServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
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
            send(response, 200, ToolService.saveStudyPlan(userId, courseId, request.getParameter("plan_text")));
        } catch (ToolException e) {
            send(response, e.getStatus(), error(e.getMessage()));
        }
    }
}
