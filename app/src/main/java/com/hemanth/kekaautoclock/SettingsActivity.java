package com.hemanth.kekaautoclock;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SettingsStore settings = new SettingsStore(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);

        EditText latField = labeledField(layout, "Office latitude", String.valueOf(settings.getLat()));
        EditText lngField = labeledField(layout, "Office longitude", String.valueOf(settings.getLng()));
        EditText radiusField = labeledField(layout, "Radius (meters)", String.valueOf(settings.getRadius()));
        EditText startField = labeledField(layout, "Clock-out window start hour (0-23)", String.valueOf(settings.getClockoutStartHour()));
        EditText endField = labeledField(layout, "Clock-out window end hour (0-24)", String.valueOf(settings.getClockoutEndHour()));

        TextView note = new TextView(this);
        note.setPadding(0, 20, 0, 20);
        note.setText("Clock-out only fires if you leave the geofence with the current hour "
                + "inside [start, end). Example: 18 to 24 means 6:00 PM through 11:59 PM.");
        layout.addView(note);

        Button saveButton = new Button(this);
        saveButton.setText("Save and re-arm geofence");
        saveButton.setOnClickListener(v -> {
            try {
                double lat = Double.parseDouble(latField.getText().toString().trim());
                double lng = Double.parseDouble(lngField.getText().toString().trim());
                float radius = Float.parseFloat(radiusField.getText().toString().trim());
                int start = Integer.parseInt(startField.getText().toString().trim());
                int end = Integer.parseInt(endField.getText().toString().trim());

                if (start < 0 || start > 23 || end < 0 || end > 24 || end <= start) {
                    Toast.makeText(this, "Invalid clock-out window", Toast.LENGTH_LONG).show();
                    return;
                }

                settings.setLocation(lat, lng, radius);
                settings.setClockoutWindow(start, end);

                Toast.makeText(this, "Saved. Re-arming geofence...", Toast.LENGTH_SHORT).show();
                new GeofenceManager(this).register(() ->
                        Toast.makeText(this, "Geofence re-armed with new settings", Toast.LENGTH_SHORT).show());

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Enter valid numbers in all fields", Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(saveButton);

        setContentView(layout);
        setTitle("Keka Auto Clock - Settings");
    }

    private EditText labeledField(LinearLayout parent, String label, String initialValue) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setPadding(0, 20, 0, 5);
        parent.addView(tv);

        EditText editText = new EditText(this);
        editText.setText(initialValue);
        parent.addView(editText);
        return editText;
    }
}
