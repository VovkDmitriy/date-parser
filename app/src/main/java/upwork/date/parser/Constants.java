/**
 * Constants holds default configuration values used throughout the application.
 */
package upwork.date.parser;

public class Constants {

    /**
     * Default URL to monitor when no custom URL is provided.
     */
    public static final String DEFAULT_URL = "http://pagemonitor.office.com.ro/pagina1.html";

    /**
     * Default polling interval in minutes between checks.
     */
    public static final int DEFAULT_INTERVAL = 15;

    /**
     * Default target phrase to compare against the parsed value.
     */
    public static final String DEFAULT_TARGET_PHRASE = "31.10.2025";

    /**
     * Initial delayed start in minutes before the first background check.
     */
    public static final long DELAYED_START = 2L;
}