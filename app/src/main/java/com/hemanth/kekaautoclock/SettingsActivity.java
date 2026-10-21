package com.hemanth.kekaautoclock;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends Activity {

    private int clockoutStartHour;
    private int clockoutEndHour;
    private MaterialButton startTimeButton;
    private MaterialButton endTimeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SettingsStore settings = new SettingsStore(this);
        clockoutStartHour = settings.getClockoutStartHour();
        clockoutEndHour = settings.getClockoutEndHour() % 24; // 24 displays as 12:00 AM (midnight)

        int dp16 = dp(16), dp24 = dp(24);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp16, dp24, dp16, dp24);
        layout.setBackgroundResource(R.drawable.card_background);

        EditText latField = labeledField(layout, "Office latitude", String.valueOf(settings.getLat()));
        EditText lngField = labeledField(layout, "Office longitude", String.valueOf(settings.getLng()));
        EditText radiusField = labeledField(layout, "Radius (meters)", String.valueOf(settings.getRadius()));

        TextView startLabel = new TextView(this);
        startLabel.setText("Clock-out window starts at");
        startLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        startLabel.setPadding(0, dp(16), 0, dp(4));
        layout.addView(startLabel);

        startTimeButton = timePickerButton(layout, clockoutStartHour, hour -> clockoutStartHour = hour);

        TextView endLabel = new TextView(this);
        endLabel.setText("Clock-out window ends at");
        endLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        endLabel.setPadding(0, dp(16), 0, dp(4));
        layout.addView(endLabel);

        endTimeButton = timePickerButton(layout, clockoutEndHour, hour -> clockoutEndHour = hour);

        TextView note = new TextView(this);
        note.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        note.setPadding(0, dp(16), 0, dp(16));
        note.setText("Clock-out only fires if you leave the geofence with the current time "
                + "inside this window. E.g. 6:00 PM to 12:00 AM covers the whole evening.");
        layout.addView(note);

        MaterialButton saveButton = new MaterialButton(this);
        saveButton.setText("Save and re-arm geofence");
        saveButton.setAllCaps(false);
        saveButton.setCornerRadius(dp(14));
        saveButton.setBackgroundResource(R.drawable.button_gradient);
        saveButton.setBackgroundTintList(null);
        saveButton.setTextColor(0xFFFFFFFF);
        saveButton.setOnClickListener(v -> {
            try {
                double lat = Double.parseDouble(latField.getText().toString().trim());
                double lng = Double.parseDouble(lngField.getText().toString().trim());
                float radius = Float.parseFloat(radiusField.getText().toString().trim());

                // Internal storage stays 0-23 for start, 1-24 for end (24 = midnight/end of day),
                // matching the existing hour-of-day comparison logic in GeofenceBroadcastReceiver.
                int end = (clockoutEndHour == 0) ? 24 : clockoutEndHour;
                int start = clockoutStartHour;

                if (end <= start) {
                    Toast.makeText(this, "End time must be after start time (use 12:00 AM for midnight)", Toast.LENGTH_LONG).show();
                    return;
                }

                settings.setLocation(lat, lng, radius);
                settings.setClockoutWindow(start, end);

                Toast.makeText(this, "Saved. Re-arming geofence...", Toast.LENGTH_SHORT).show();
                new GeofenceManager(this).register(() ->
                        Toast.makeText(this, "Geofence re-armed with new settings", Toast.LENGTH_SHORT).show());

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Enter valid numbers in location fields", Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(saveButton);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_dark));
        scrollView.setPadding(dp16, dp16, dp16, dp16);
        scrollView.addView(layout);
        setContentView(scrollView);
        setTitle("Keka Auto Clock - Settings");
    }

    private interface HourPicked {
        void onPicked(int hourOfDay);
    }

    private MaterialButton timePickerButton(LinearLayout parent, int initialHour, HourPicked callback) {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(TimeUtils.formatHour12(initialHour));
        button.setAllCaps(false);
        button.setCornerRadius(dp(12));
        button.setOnClickListener(v -> new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            callback.onPicked(hourOfDay);
            button.setText(TimeUtils.formatHour12(hourOfDay));
        }, initialHour, 0, false).show()); // false = 12-hour display with AM/PM
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        button.setLayoutParams(params);
        parent.addView(button);
        return button;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private EditText labeledField(LinearLayout parent, String label, String initialValue) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tv.setPadding(0, dp(16), 0, dp(4));
        parent.addView(tv);

        EditText editText = new EditText(this);
        editText.setText(initialValue);
        editText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        parent.addView(editText);
        return editText;
    }
}
