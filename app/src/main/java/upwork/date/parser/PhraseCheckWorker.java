/**
 * Worker that periodically fetches the monitored page, parses the date value inside
 * a script tag containing '#minmax', and starts the AlarmService if the parsed date
 * does not match the saved target phrase.
 */
package upwork.date.parser;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhraseCheckWorker extends Worker {

    /**
     * Constructor called by WorkManager to instantiate the worker.
     * @param ctx Application context
     * @param params Worker parameters
     */
    public PhraseCheckWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    /**
     * Executes the background work: fetches the URL, extracts the date pattern,
     * and triggers the AlarmService if the date differs from the expected value.
     * @return Result.success() if work completed, Result.retry() on failure
     */
    @NonNull
    @Override
    public Result doWork() {
        String url      = SaveManager.getUrl(getApplicationContext());
        String expected = SaveManager.getTarget(getApplicationContext());

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
                String js = script.data();
                Matcher m = datePattern.matcher(js);
                if (m.find()) {
                    date = m.group(1);
                    break;
                }
            }

            if (date == null) {
                throw new IOException("Failed to parse date in scripts");
            }

            if (!date.equals(expected) && !AlarmService.isServiceRunning()) {
                Intent intent = new Intent(getApplicationContext(), AlarmService.class)
                        .setAction(AlarmService.ACTION_START);
                getApplicationContext().startForegroundService(intent);
            }

            return Result.success();

        } catch (Exception e) {
            return Result.retry();
        }
    }
}