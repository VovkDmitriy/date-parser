/**
 * AlarmService is a foreground Android Service that plays an alarm ringtone
 * when a monitored phrase does not match the expected value. After a timeout,
 * it stops the alarm and posts a simple notification to inform the user.
 */
package upwork.date.parser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AlarmService extends Service {
    /**
     * Action string to start the alarm service
     */
    public static final String ACTION_START = "ACTION_START";

    /**
     * Action string to stop the alarm service
     */
    public static final String ACTION_STOP = "ACTION_STOP";

    private static final int NOTIF_FOREGROUND_ID = 1;
    private static final int NOTIF_SIMPLE_ID = 2;

    private Ringtone ringtone;
    private CountDownTimer timer;
    private String channelId = "alarm_service";

    /**
     * Called when the service receives a start request via startService or startForegroundService.
     * @param intent The Intent supplied to startService
     * @param flags Additional data about the start request
     * @param startId A unique integer representing this specific request to start
     * @return START_STICKY to indicate the service should remain running
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_START.equals(intent.getAction())) {
            startForeground(NOTIF_FOREGROUND_ID, buildForegroundNotification());
            playAlarmWithTimeout();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
        }
        return START_STICKY;
    }

    /**
     * Builds the notification shown while the service is in the foreground.
     * @return A Notification configured for the foreground service
     */
    private Notification buildForegroundNotification() {
        createChannelIfNeeded();
        Intent stopIntent = new Intent(this, AlarmService.class)
                .setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getApplicationContext().getString(R.string.phrase_does_not_match))
                .setContentText(getApplicationContext().getString(R.string.press_stop_to_disable))
                .setSmallIcon(R.drawable.baseline_access_alarm_24)
                .addAction(R.drawable.baseline_stop_24,
                        getApplicationContext().getString(R.string.stop), pi)
                .setOngoing(true)
                .build();
    }

    /**
     * Plays the alarm ringtone in a loop and schedules a timeout to stop it.
     */
    private void playAlarmWithTimeout() {
        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), alert);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.setLooping(true);
        }
        ringtone.play();

        timer = new CountDownTimer(10 * 60 * 1000, 10 * 60 * 1000) {
            @Override public void onTick(long millisUntilFinished) { }
            @Override public void onFinish() {
                if (ringtone.isPlaying()) ringtone.stop();
                stopForeground(true);
                sendSimpleNotification();
                stopSelf();
            }
        }.start();
    }

    /**
     * Sends a one-time notification to inform the user that the monitored phrase does not match.
     */
    private void sendSimpleNotification() {
        createChannelIfNeeded();
        Notification simple = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getApplicationContext().getString(R.string.phrase_does_not_match))
                .setSmallIcon(R.drawable.baseline_access_alarm_24)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_SIMPLE_ID, simple);
    }

    /**
     * Creates a NotificationChannel if running on Android O or higher.
     */
    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    channelId,
                    "Alarm Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            chan.setShowBadge(true);
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(chan);
        }
    }

    /**
     * Called when the service is being destroyed. Stops any playing ringtone and cancels the timer.
     */
    @Override
    public void onDestroy() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (timer != null) {
            timer.cancel();
        }
        super.onDestroy();
    }

    /**
     * Binding is not supported for this service, so always return null.
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}