/**
 * PhraseParser provides an asynchronous helper to fetch and parse the target phrase
 * (date) from the monitored page's embedded script tag.
 */
package upwork.date.parser;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhraseParser {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Callback interface for receiving parsed phrase or error asynchronously.
     */
    public interface OnPhraseParsedListener {

        /**
         * Invoked when the target phrase has been successfully parsed.
         * @param phrase Parsed date string in format DD.MM.YYYY
         */
        void onDateParsed(@NonNull String phrase);

        /**
         * Invoked when an error occurs during parsing or network request.
         * @param error Exception encountered during parsing
         */
        void onError(@NonNull Exception error);
    }

    /**
     * Asynchronously connects to the given URL, extracts the date string from
     * a <script> element containing "#minmax", and invokes the listener callbacks
     * on the main thread.
     * @param url      Page URL to fetch and parse
     * @param listener Listener to receive onDateParsed or onError callbacks
     */
    public static void parsePhraseAsync(
            @NonNull final String url,
            @NonNull final OnPhraseParsedListener listener) {
        executor.execute(() -> {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Chrome")
                        .ignoreHttpErrors(true)
                        .get();

                Elements scripts = doc.select("script");
                Pattern datePattern = Pattern.compile("-\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");
                String date = null;

                for (Element script : scripts) {
                    if (!script.toString().contains("#minmax")) {
                        continue;
                    }
                    Matcher m = datePattern.matcher(script.data());
                    if (m.find()) {
                        date = m.group(1);
                        break;
                    }
                }

                if (date != null) {
                    final String result = date;
                    mainHandler.post(() -> listener.onDateParsed(result));
                } else {
                    throw new IOException("Failed to parse date in script");
                }

            } catch (Exception e) {
                mainHandler.post(() -> listener.onError(e));
            }
        });
    }
}