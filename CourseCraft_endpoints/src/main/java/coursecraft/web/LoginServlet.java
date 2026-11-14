package coursecraft.web;

import coursecraft.util.DbUtil;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;

/** POST /login with email + password. Creates the session the API servlets check. */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String ctx = request.getContextPath();
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            response.sendRedirect(ctx + "/index.html?error=1");
            return;
        }

        Integer userId = null;
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement("SELECT id, password_hash FROM users WHERE email = ?");
            ps.setString(1, email.trim());
            rs = ps.executeQuery();
            if (rs.next()) {
                try {
                    if (BCrypt.checkpw(password, rs.getString("password_hash"))) {
                        userId = rs.getInt("id");
                    }
                } catch (IllegalArgumentException badHash) {
                    // stored value is not a valid bcrypt hash (e.g. still the placeholder): treat as failed login
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
            return;
        } finally {
            DbUtil.close(rs, ps, conn);
        }

        if (userId == null) {
            response.sendRedirect(ctx + "/index.html?error=1");
            return;
        }
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();                      // new session after login (prevents session fixation)
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", userId);
        session.setAttribute("email", email.trim());
        session.setMaxInactiveInterval(30 * 60);
        response.sendRedirect(ctx + "/agent.html");
    }
}
