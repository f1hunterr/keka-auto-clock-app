package com.hemanth.kekaautoclock;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

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

        int dp8 = dp(8), dp16 = dp(16), dp24 = dp(24);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp16, dp24, dp16, dp24);

        TextView title = new TextView(this);
        title.setText("Keka Auto Clock");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setPadding(0, 0, 0, dp16);
        page.addView(title);

        // --- Status card ---
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackgroundResource(R.drawable.card_background);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.bottomMargin = dp16;
        statusCard.setLayoutParams(statusParams);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setLineSpacing(dp(4), 1f);
        statusCard.addView(statusText);
        page.addView(statusCard);

        // --- Action buttons card ---
        LinearLayout actionsCard = new LinearLayout(this);
        actionsCard.setOrientation(LinearLayout.VERTICAL);
        actionsCard.setBackgroundResource(R.drawable.card_background);

        addButton(actionsCard, "Login to Keka",
                v -> startActivity(new Intent(this, LoginActivity.class)), dp8);
        addButton(actionsCard, "Test Clock-In Now",
                v -> runManualTest("in"), dp8);
        addButton(actionsCard, "Test Clock-Out Now",
                v -> runManualTest("out"), dp8);
        addButton(actionsCard, "Settings",
                v -> startActivity(new Intent(this, SettingsActivity.class)), dp8);
        addButton(actionsCard, "View Event Log",
                v -> startActivity(new Intent(this, LogViewerActivity.class)), dp8);
        addButton(actionsCard, "Fix Battery Settings (stops silent misses)",
                v -> openBatterySettings(), dp8);
        addButton(actionsCard, "Fix Location Permission (needs \"Allow all the time\")",
                v -> openLocationPermissionSettings(), 0);

        page.addView(actionsCard);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_dark));
        scrollView.addView(page);
        setContentView(scrollView);

        createNotificationChannel();
        requestPermissionsThenRegisterGeofences();
    }

    private void addButton(LinearLayout parent, String text, android.view.View.OnClickListener listener, int bottomMarginPx) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setCornerRadius(dp(14));
        button.setBackgroundResource(R.drawable.button_gradient);
        button.setBackgroundTintList(null); // null out Material's auto-tint so the gradient actually shows
        button.setTextColor(0xFFFFFFFF);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMarginPx;
        button.setLayoutParams(params);
        parent.addView(button);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
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
        renderStatus();
        fetchCurrentLocation();
    }

    private Double currentDistanceMeters = null;
    private Boolean currentlyInsideGeofence = null;

    private void fetchCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        com.google.android.gms.location.FusedLocationProviderClient client =
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        try {
            client.getLastLocation().addOnSuccessListener(location -> {
                if (location == null) return;
                android.location.Location office = new android.location.Location("office");
                office.setLatitude(settings.getLat());
                office.setLongitude(settings.getLng());
                currentDistanceMeters = (double) location.distanceTo(office);
                currentlyInsideGeofence = currentDistanceMeters <= settings.getRadius();
                renderStatus();
            });
        } catch (SecurityException ignored) {
            // permission was revoked between the check above and the call - just skip this refresh
        }
    }

    private void renderStatus() {
        boolean locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean backgroundLocationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean batteryUnrestricted = isIgnoringBatteryOptimizations();

        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendHeading(sb, "PERMISSIONS");
        appendStatusLine(sb, locationGranted, "Location access");
        appendStatusLine(sb, backgroundLocationGranted, "Background location (\"Allow all the time\")");
        appendStatusLine(sb, notificationsGranted, "Notifications");
        appendStatusLine(sb, batteryUnrestricted, "Battery unrestricted"
                + (batteryUnrestricted ? "" : " - set in phone Settings > Battery, or trigger stops silently"));

        sb.append("\n");
        appendHeading(sb, "LOCATION");
        if (!locationGranted) {
            appendPlainLine(sb, "Unknown - location permission not granted");
        } else if (currentlyInsideGeofence == null) {
            appendPlainLine(sb, "Checking...");
        } else {
            appendStatusLine(sb, currentlyInsideGeofence,
                    currentlyInsideGeofence ? "You are IN the office area" : "You are OUTSIDE the office area");
            appendPlainLine(sb, String.format("Distance from office: %.0fm (radius %.0fm)",
                    currentDistanceMeters, settings.getRadius()));
        }

        sb.append("\n");
        appendHeading(sb, "GEOFENCE");
        appendStatusLine(sb, settings.isGeofenceArmed(), "Armed");
        appendPlainLine(sb, String.format("Lat %.6f, Lng %.6f, radius %.0fm",
                settings.getLat(), settings.getLng(), settings.getRadius()));
        int endHour = settings.getClockoutEndHour();
        appendPlainLine(sb, String.format("Clock-out window: %s - %s",
                TimeUtils.formatHour12(settings.getClockoutStartHour()), TimeUtils.formatHour12(endHour % 24)));

        sb.append("\n");
        appendHeading(sb, "LAST EVENT");
        appendPlainLine(sb, settings.getLastEvent());

        statusText.setText(sb);
    }

    private void appendHeading(SpannableStringBuilder sb, String text) {
        int start = sb.length();
        sb.append(text).append("\n");
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, start + text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_secondary)),
                start, start + text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendStatusLine(SpannableStringBuilder sb, boolean ok, String label) {
        int dotStart = sb.length();
        String dot = ok ? "\u25CF " : "\u25CF "; // filled circle, colored below
        sb.append(dot);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, ok ? R.color.status_ok : R.color.status_warn)),
                dotStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int textStart = sb.length();
        sb.append(label).append("\n");
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_primary)),
                textStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendPlainLine(SpannableStringBuilder sb, String text) {
        int start = sb.length();
        sb.append(text).append("\n");
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_primary)),
                start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openBatterySettings() {
        try {
            if (isIgnoringBatteryOptimizations()) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } else {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open battery settings - set manually in phone Settings > Battery", Toast.LENGTH_LONG).show();
        }
    }

    private void openLocationPermissionSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
            Toast.makeText(this, "Open Permissions > Location > Allow all the time", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open app settings", Toast.LENGTH_LONG).show();
        }
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

