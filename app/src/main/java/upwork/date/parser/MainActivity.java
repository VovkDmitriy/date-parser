/**
 * MainActivity hosts the UI for configuring monitoring settings and displays
 * the current match status of the target phrase against the monitored page.
 */
package upwork.date.parser;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import upwork.date.parser.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;


    /**
     * Initializes UI and prepares edge-to-edge layout.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        initUI();
    }

    /**
     * Restores monitoring state when returning to this activity.
     */
    @Override
    protected void onResume() {
        super.onResume();
        setMonitoring();
    }

    /**
     * Sets up UI fields with saved values and listeners.
     */
    private void initUI() {
        binding.timeInterval.setText(String.valueOf(SaveManager.getInterval(this)));
        binding.urlField.setText(SaveManager.getUrl(this));
        binding.targetPhrase.setText(SaveManager.getTarget(this));
        setListeners();
        checkInputs();
        checkNotificationPermission();
    }

    /**
     * Attaches click and text change listeners to interactive views.
     */
    private void setListeners() {
        binding.startStopBtn.setOnClickListener(view -> {
            if (SaveManager.isMonitoring(this) || binding.startStopText.getText().equals(getString(R.string.stop))) {
                stopMonitoring();
                SaveManager.isMonitoring(this, false);
            } else {
                startMonitoring(SaveManager.getInterval(this));
                SaveManager.isMonitoring(this, true);
            }
            setMonitoring();
        });

        binding.checkNowBtn.setOnClickListener(view -> checkTargetPhrase());
        binding.applyBtn.setOnClickListener(view -> applyInputs());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { checkInputs(); }
        };

        binding.timeInterval.addTextChangedListener(watcher);
        binding.urlField.addTextChangedListener(watcher);
        binding.targetPhrase.addTextChangedListener(watcher);
    }

    /**
     * Validates input fields and toggles the Apply button visibility.
     */
    private void checkInputs() {
        boolean showBtn = false;
        String interval = binding.timeInterval.getText().toString();
        if (!interval.equals(String.valueOf(SaveManager.getInterval(this)))) {
            showBtn = true;
        }
        String url = binding.urlField.getText().toString();
        if (!url.equals(SaveManager.getUrl(this))) {
            showBtn = true;
        }
        String target = binding.targetPhrase.getText().toString();
        if (!target.equals(SaveManager.getTarget(this))) {
            showBtn = true;
        }
        binding.applyBtn.setVisibility(showBtn ? View.VISIBLE : View.GONE);
    }

    /**
     * Applies and saves user inputs, then triggers an immediate check.
     */
    private void applyInputs() {
        int interval;
        try {
            interval = Integer.parseInt(binding.timeInterval.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.incorrect_format), Toast.LENGTH_SHORT).show();
            return;
        }
        if (interval < Constants.DEFAULT_INTERVAL) {
            Toast.makeText(this, getString(R.string.the_interval_cannot_be_less_than_15_minutes), Toast.LENGTH_SHORT).show();
            return;
        }
        String url = binding.urlField.getText().toString();
        if (url.isBlank()) {
            Toast.makeText(this, getString(R.string.link_cannot_be_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        String target = binding.targetPhrase.getText().toString();
        if (target.isBlank()) {
            Toast.makeText(this, getString(R.string.target_phrase_cannot_be_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        binding.applyBtn.setVisibility(View.GONE);
        SaveManager.setInterval(this, interval);
        SaveManager.setUrl(this, url);
        SaveManager.setTarget(this, target);
        checkTargetPhrase();
        if (SaveManager.isMonitoring(this)) {
            startMonitoring(interval);
        }
    }

    /**
     * Updates UI elements based on whether monitoring is active.
     */
    private void setMonitoring() {
        if (SaveManager.isMonitoring(this)) {
            binding.titleLabel.setText(getString(R.string.monitoring));
            binding.startStopText.setText(getString(R.string.stop));
            binding.matchCard.setVisibility(View.VISIBLE);
            binding.startStopText.setTextColor(getColor(R.color.button_stop_text));
            checkTargetPhrase();
        } else {
            binding.titleLabel.setText(getString(R.string.stopped));
            binding.startStopText.setText(getString(R.string.start));
            binding.startStopText.setTextColor(getColor(R.color.button_start_text));
            binding.matchCard.setVisibility(View.GONE);
        }
    }

    /**
     * Initiates an asynchronous check of the target phrase via WorkManager.
     */
    private void startMonitoring(int intervalMinutes) {
        stopMonitoring();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                PhraseCheckWorker.class, intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInitialDelay(Constants.DELAYED_START, TimeUnit.MINUTES)
                .addTag("date_check")
                .build();
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "date_check",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest
                );
    }

    /**
     * Cancels active monitoring jobs and stops any active alarm service.
     */
    private void stopMonitoring() {
        WorkManager.getInstance(this)
                .cancelUniqueWork("date_check");
        Intent intent = new Intent(this, AlarmService.class)
                .setAction(AlarmService.ACTION_STOP);
        startService(intent);
    }

    /**
     * Triggers a one-time asynchronous check of the current target phrase.
     */
    private void checkTargetPhrase() {
        PhraseParser.parsePhraseAsync(
                SaveManager.getUrl(this),
                new PhraseParser.OnPhraseParsedListener() {
                    @Override
                    public void onDateParsed(@NonNull String phrase) {
                        checkPhraseMatching(phrase);
                    }
                    @Override
                    public void onError(@NonNull Exception error) {
                        Toast.makeText(
                                        MainActivity.this,
                                        error.getLocalizedMessage(),
                                        Toast.LENGTH_SHORT)
                                .show();
                        binding.matchCard.setVisibility(View.GONE);
                    }
                }
        );
    }

    /**
     * Displays the parsed phrase and updates UI color based on match result.
     */
    private void checkPhraseMatching(String phrase) {
        binding.matchCard.setVisibility(View.VISIBLE);
        binding.parsedResult.setText(phrase);
        boolean isMatch = phrase.equals(SaveManager.getTarget(this));
        int colorRes = isMatch ? R.color.match_color : R.color.not_match_color;
        int textRes = isMatch ? R.string.match : R.string.not_match;
        binding.matchCard.setCardBackgroundColor(getColor(colorRes));
        binding.matchText.setTextColor(getColor(colorRes));
        binding.matchText.setText(getString(textRes));
        binding.parsedResult.setTextColor(getColor(colorRes));

        if (!isMatch){
            if (!AlarmService.isServiceRunning()) {
                Intent intent2 = new Intent(this, AlarmService.class)
                        .setAction(AlarmService.ACTION_START);
                startForegroundService(intent2);
                binding.startStopText.setText(getString(R.string.stop));
                binding.startStopText.setTextColor(getColor(R.color.button_stop_text));
            }
        }else{
            if (AlarmService.isServiceRunning()) {
                Intent intent2 = new Intent(this, AlarmService.class)
                        .setAction(AlarmService.ACTION_STOP);
                startService(intent2);
            }
        }
        SaveManager.setLastState(this, isMatch);
    }

    void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{ android.Manifest.permission.POST_NOTIFICATIONS },
                        101
                );
            }
        }
    }
}