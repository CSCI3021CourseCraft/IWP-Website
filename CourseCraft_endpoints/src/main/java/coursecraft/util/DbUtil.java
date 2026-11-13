package coursecraft.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens JDBC connections using the settings in config.properties. */
public class DbUtil {

    static {
        try {
            Class.forName("org.postgresql.Driver");      // makes sure the driver is loaded in Tomcat
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        String url = Config.get("db.url");
        if (url == null) {
            throw new SQLException("DB_URL is not set (set the DB_URL, DB_USER and DB_PASSWORD environment variables)");
        }
        return DriverManager.getConnection(url, Config.get("db.user"), Config.get("db.password"));
    }

    /** Closes anything quietly; call it in a finally block. */
    public static void close(AutoCloseable... items) {
        for (AutoCloseable item : items) {
            if (item != null) {
                try {
                    item.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
