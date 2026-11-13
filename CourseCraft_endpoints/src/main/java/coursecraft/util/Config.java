package coursecraft.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads settings from config.properties (on the classpath, NOT committed to Git).
 * If a key is missing there, it falls back to an environment variable:
 * db.url -> DB_URL, llm.api.key -> LLM_API_KEY.
 */
public class Config {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static String get(String key) {
        String v = PROPS.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            v = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    public static String get(String key, String defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : v;
    }
}
