package com.hemanth.kekaautoclock;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String CHANNEL_ID = "keka_auto_clock";
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_BACKGROUND_LOCATION = 1002;

    private TextView statusText;
    private SettingsStore settings;
    private EventLog eventLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = new SettingsStore(this);
        eventLog = new EventLog(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);

        TextView title = new TextView(this);
        title.setTextSize(20);
        title.setText("Keka Auto Clock");
        layout.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setPadding(0, 20, 0, 20);
        layout.addView(statusText);

        Button loginButton = new Button(this);
        loginButton.setText("Login to Keka");
        loginButton.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        layout.addView(loginButton);

        Button testInButton = new Button(this);
        testInButton.setText("Test Clock-In Now");
        testInButton.setOnClickListener(v -> runManualTest("in"));
        layout.addView(testInButton);

        Button testOutButton = new Button(this);
        testOutButton.setText("Test Clock-Out Now");
        testOutButton.setOnClickListener(v -> runManualTest("out"));
        layout.addView(testOutButton);

        Button settingsButton = new Button(this);
        settingsButton.setText("Settings (location, times)");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        layout.addView(settingsButton);

        Button logButton = new Button(this);
        logButton.setText("View Event Log");
        logButton.setOnClickListener(v -> startActivity(new Intent(this, LogViewerActivity.class)));
        layout.addView(logButton);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);
        setContentView(scrollView);

        createNotificationChannel();
        requestPermissionsThenRegisterGeofences();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus(); // picks up changes made in Settings or by background triggers
    }

    private void runManualTest(String mode) {
        eventLog.log("Manual test triggered: " + mode);
        Intent intent = new Intent(this, ClockActionActivity.class);
        intent.putExtra(ClockActionActivity.EXTRA_MODE, mode);
        startActivity(intent);
        Toast.makeText(this, "Running test " + mode + " - watch for the notification", Toast.LENGTH_SHORT).show();
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();

        boolean locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean backgroundLocationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean batteryUnrestricted = isIgnoringBatteryOptimizations();

        sb.append("PERMISSIONS\n");
        sb.append(dot(locationGranted)).append(" Location access\n");
        sb.append(dot(backgroundLocationGranted)).append(" Background location (\"Allow all the time\")\n");
        sb.append(dot(notificationsGranted)).append(" Notifications\n");
        sb.append(dot(batteryUnrestricted)).append(" Battery unrestricted");
        if (!batteryUnrestricted) {
            sb.append(" <- set this in phone Settings > Battery, or trigger will stop silently");
        }
        sb.append("\n\n");

        sb.append("GEOFENCE\n");
        sb.append(dot(settings.isGeofenceArmed())).append(" Armed\n");
        sb.append(String.format("Lat %.6f, Lng %.6f, radius %.0fm\n",
                settings.getLat(), settings.getLng(), settings.getRadius()));
        sb.append(String.format("Clock-out window: %02d:00 - %02d:00\n\n",
                settings.getClockoutStartHour(), settings.getClockoutEndHour()));

        sb.append("LAST EVENT\n");
        sb.append(settings.getLastEvent());

        statusText.setText(sb.toString());
    }

    private String dot(boolean ok) {
        return ok ? "[OK]" : "[!!]";
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Keka Auto Clock", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private void requestPermissionsThenRegisterGeofences() {
        List<String> missing = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_LOCATION);
            return;
        }
        requestBackgroundLocationIfNeeded();
    }

    private void requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Next: allow location 'All the time' for background geofencing",
                    Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQ_BACKGROUND_LOCATION);
            return;
        }
        new GeofenceManager(this).register(this::refreshStatus);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            requestBackgroundLocationIfNeeded();
        } else if (requestCode == REQ_BACKGROUND_LOCATION) {
            new GeofenceManager(this).register(this::refreshStatus);
        }
        refreshStatus();
    }
}

