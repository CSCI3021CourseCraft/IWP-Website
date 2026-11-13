package coursecraft.web;

import coursecraft.service.ToolException;
import coursecraft.service.ToolService;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Tool: get_course_materials.  GET /api/tools/get_course_materials?course_id=1&type=quiz */
@WebServlet("/api/tools/get_course_materials")
public class MaterialsToolServlet extends BaseApiServlet {

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
            send(response, 200, ToolService.getCourseMaterials(userId, courseId, request.getParameter("type")));
        } catch (ToolException e) {
            send(response, e.getStatus(), error(e.getMessage()));
        }
    }
}
