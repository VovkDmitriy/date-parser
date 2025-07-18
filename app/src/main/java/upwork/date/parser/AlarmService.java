package upwork.date.parser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Foreground service that periodically checks a webpage for a date pattern
 * and triggers an alarm ringtone if the dates differ.
 */
public class AlarmService extends Service {

    public static boolean isServiceRun = false;

    public static final String ACTION_START            = "ACTION_START";
    public static final String ACTION_UPDATE_INTERVAL  = "ACTION_UPDATE_INTERVAL";
    public static final String ACTION_STOP_ALARM       = "ACTION_STOP_ALARM";
    public static final String ACTION_STOP_SERVICE     = "ACTION_STOP_SERVICE";

    private static final String CHANNEL_ID       = "alarm_service_channel";
    private static final int    NOTIF_ID_FORE    = 1001;
    private static final int    NOTIF_ID_SIMPLE  = 1002;

    private long intervalMinutes;

    private ScheduledExecutorService checkScheduler;
    private ScheduledExecutorService stopScheduler;
    private ScheduledFuture<?> stopFuture;
    private Ringtone ringtone;

    /**
     * Called when the service is created. Initializes schedulers and ringtone.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        isServiceRun = true;
        createChannel();
        checkScheduler = Executors.newSingleThreadScheduledExecutor();
        stopScheduler  = Executors.newSingleThreadScheduledExecutor();
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = RingtoneManager.getRingtone(this, alarmUri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.setLooping(true);
        }
    }

    /**
     * Handles start commands for the service.
     * @param intent the Intent supplied to startService or startForegroundService
     * @param flags additional data about the start request
     * @param startId a unique integer representing this specific request
     * @return START_STICKY to indicate the service should remain running
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_START.equals(action)) {
            intervalMinutes = SaveManager.getInterval(getApplicationContext());
            startForeground(NOTIF_ID_FORE, buildOngoingNotification(true));
            startChecking();

        } else if (ACTION_UPDATE_INTERVAL.equals(action)) {
            long newInterval = SaveManager.getInterval(getApplicationContext());
            updateInterval(newInterval);

        } else if (ACTION_STOP_ALARM.equals(action)) {
            if (ringtone.isPlaying()) ringtone.stop();
            cancelScheduledStop();
            notifyForeground(false);

        } else if (ACTION_STOP_SERVICE.equals(action)) {
            stopServiceTasks();
            stopSelf();
        }

        return START_STICKY;
    }

    /**
     * Schedules periodic checks at the current interval.
     */
    private void startChecking() {
        if (checkScheduler.isShutdown()) return;
        checkScheduler.scheduleWithFixedDelay(
                this::checkAndAlarm,
                0,
                intervalMinutes,
                TimeUnit.MINUTES
        );
    }

    /**
     * Updates the checking interval at runtime and restarts the scheduler.
     * @param newInterval new interval in minutes
     */
    private void updateInterval(long newInterval) {
        this.intervalMinutes = newInterval;
        if (!checkScheduler.isShutdown()) {
            checkScheduler.shutdownNow();
            checkScheduler = Executors.newSingleThreadScheduledExecutor();
            startChecking();
            notifyForeground(true);
        }
    }

    /**
     * Performs the HTTP request, parses the date, and triggers alarm if needed.
     */
    private void checkAndAlarm() {
        try {
            String url      = SaveManager.getUrl(getApplicationContext());
            String expected = SaveManager.getTarget(getApplicationContext());

            Document doc = Jsoup.connect(url)
                    .userAgent("Chrome")
                    .ignoreHttpErrors(true)
                    .get();
            Elements scripts = doc.select("script");
            String found = scripts.stream()
                    .map(s -> s.data())
                    .filter(d -> d.contains("#minmax"))
                    .map(d -> {
                        java.util.regex.Matcher m = java.util.regex.Pattern
                                .compile("-\\s*(\\d{2}\\.\\d{2}\\.\\d{4})")
                                .matcher(d);
                        return m.find() ? m.group(1) : null;
                    })
                    .filter(txt -> txt != null)
                    .findFirst()
                    .orElseThrow(() -> new IOException("Date not found"));

            if (!found.equals(expected)) {
                if (!ringtone.isPlaying()) {
                    ringtone.play();
                    scheduleStop();
                    notifyForeground(true);
                }
            } else {
                if (ringtone.isPlaying()) ringtone.stop();
                cancelScheduledStop();
                notifyForeground(false);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Schedules automatic stop of the ringtone after 2 minutes.
     */
    private void scheduleStop() {
        cancelScheduledStop();
        stopFuture = stopScheduler.schedule(() -> {
            if (ringtone.isPlaying()) ringtone.stop();
            notifyForeground(false);
        }, Constants.NOTIFICATION_TIME, TimeUnit.MINUTES);
    }

    /**
     * Cancels any scheduled stop tasks.
     */
    private void cancelScheduledStop() {
        if (stopFuture != null && !stopFuture.isDone()) {
            stopFuture.cancel(true);
        }
    }

    /**
     * Builds the ongoing foreground notification.
     * @param withStopAction whether to include the stop button action
     * @return configured Notification
     */
    private Notification buildOngoingNotification(boolean withStopAction) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.monitoring))
                .setContentText("Interval: " +  intervalMinutes + " min")
                .setSmallIcon(R.drawable.baseline_access_alarm_24)
                .setOngoing(true);

        if (withStopAction) {
            Intent stopAlarm = new Intent(this, AlarmService.class)
                    .setAction(ACTION_STOP_ALARM);
            PendingIntent piStop = PendingIntent.getService(
                    this, 0, stopAlarm, PendingIntent.FLAG_IMMUTABLE
            );
            b.addAction(R.drawable.baseline_stop_24, getString(R.string.stop), piStop);
        }
        return b.build();
    }

    /**
     * Updates the foreground notification display.
     * @param withStopAction whether to include the stop button action
     */
    private void notifyForeground(boolean withStopAction) {
        Notification notif = buildOngoingNotification(withStopAction);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID_FORE, notif);
    }

    /**
     * Creates the notification channel if needed.
     */
    private void createChannel() {
        NotificationChannel chan = new NotificationChannel(
                CHANNEL_ID,
                "Alarm Service Channel",
                NotificationManager.IMPORTANCE_HIGH
        );
        chan.setShowBadge(true);
        getSystemService(NotificationManager.class).createNotificationChannel(chan);
    }

    /**
     * Stops all service tasks and removes the foreground status.
     */
    private void stopServiceTasks() {
        if (!checkScheduler.isShutdown()) checkScheduler.shutdownNow();
        if (!stopScheduler.isShutdown()) stopScheduler.shutdownNow();
        if (ringtone.isPlaying()) ringtone.stop();
        stopForeground(true);
    }

    /**
     * Called when the service is destroyed. Cleans up resources.
     */
    @Override
    public void onDestroy() {
        stopServiceTasks();
        super.onDestroy();
        isServiceRun = false;
    }

    /**
     * Binding is not supported. Returns null.
     * @param intent the Intent that was used to bind to this service
     * @return null
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}