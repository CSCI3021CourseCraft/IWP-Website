package coursecraft.web;

import coursecraft.util.DbUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Visit /dbtest to confirm Tomcat can reach Supabase (PostgreSQL). Delete this servlet before the final project. */
@WebServlet("/dbtest")
public class DbTestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement("SELECT (SELECT COUNT(*) FROM users) AS users, "
                    + "(SELECT COUNT(*) FROM courses) AS courses, (SELECT COUNT(*) FROM notes) AS notes");
            rs = ps.executeQuery();
            if (rs.next()) {
                out.println("Connected to Supabase PostgreSQL OK.");
                out.println("users=" + rs.getInt("users") + ", courses=" + rs.getInt("courses")
                        + ", notes=" + rs.getInt("notes"));
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println("Database connection FAILED: " + e.getMessage());
        } finally {
            DbUtil.close(rs, ps, conn);
        }
    }
}
