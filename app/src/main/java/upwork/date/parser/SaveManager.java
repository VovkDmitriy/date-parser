/**
 * SaveManager handles storing and retrieving user settings in SharedPreferences.
 */
package upwork.date.parser;

import android.content.Context;

public class SaveManager {

    /**
     * Retrieves the saved URL or returns the default if not set.
     * @param context Application context
     * @return The stored URL string
     */
    public static String getUrl(Context context) {
        return context.getSharedPreferences("TABLE", Context.MODE_PRIVATE)
                .getString("parse_url", Constants.DEFAULT_URL);
    }

    /**
     * Saves the provided URL in SharedPreferences.
     * @param context Application context
     * @param url URL string to store
     */
    public static void setUrl(Context context, String url) {
        context.getSharedPreferences("TABLE", Context.MODE_PRIVATE)
                .edit()
                .putString("parse_url", url)
                .apply();
    }

    /**
     * Retrieves the saved polling interval in minutes or returns the default if not set.
     * @param context Application context
     * @return The stored interval value
     */
    public static int getInterval(Context context) {
        return context.getSharedPreferences("TABLE", Context.MODE_PRIVATE)
                .getInt("parse_interval", Constants.DEFAULT_INTERVAL);
    }

    /**
     * Saves the polling interval value in SharedPreferences.
     * @param context Application context
     * @param interval Interval in minutes to store
     */
    public static void setInterval(Context context, int interval) {
        context.getSharedPreferences("TABLE", Context.MODE_PRIVATE)
                .edit()
                .putInt("parse_interval", interval)
                .apply();
    }

    /**
     * Retrieves the saved target phrase or returns the default if not set.
     * @param context Application context
     * @return The stored target phrase
     */
    public static String getTarget(Context context) {
        return context.getSharedPreferences("TABLE", Context.MODE_PRIVATE)
                .getString("target_phrase", Constants.DEFAULT_TARGET_PHRASE);
    }

    /**
     * Saves the target phrase in SharedPreferences.
     * @param context Application context
     * @param phrase The phrase to store
     */
    public static void setTarget(Context context, String phrase) {
        context.getSharedPreferences("TABLE", Context.MODE_PRIVATE)
                .edit()
                .putString("target_phrase", phrase)
                .apply();
    }

    /**
     * Checks whether background monitoring is currently enabled.
     * @param context Application context
     * @return True if monitoring is active, false otherwise
     */
    public static boolean isMonitoring(Context context) {
        return context.getSharedPreferences("TABLE", Context.MODE_PRIVATE)
                .getBoolean("is_monitoring", false);
    }

    /**
     * Sets the monitoring active state in SharedPreferences.
     * @param context Application context
     * @param isMonitoring True to enable monitoring, false to disable
     */
    public static void isMonitoring(Context context, boolean isMonitoring) {
        context.getSharedPreferences("TABLE", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_monitoring", isMonitoring)
                .apply();
    }
}