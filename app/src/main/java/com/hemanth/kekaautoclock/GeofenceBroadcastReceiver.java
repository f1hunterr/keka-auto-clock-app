package com.hemanth.kekaautoclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.Calendar;

/** Fired by Play Services whenever the office geofence transitions. */
public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "KekaAutoClock";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        EventLog eventLog = new EventLog(context);

        if (event == null || event.hasError()) {
            String err = "Geofencing event error: " + (event != null ? event.getErrorCode() : "null event");
            Log.e(TAG, err);
            eventLog.log(err);
            return;
        }

        int transition = event.getGeofenceTransition();
        SettingsStore settings = new SettingsStore(context);

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.i(TAG, "Entered office geofence -> clock-in");
            eventLog.log("Geofence ENTER -> clock-in triggered");
            launchClockAction(context, "in");

        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            if (hour >= settings.getClockoutStartHour() && hour < settings.getClockoutEndHour()) {
                Log.i(TAG, "Exited office geofence within clock-out window -> clock-out");
                eventLog.log("Geofence EXIT at hour=" + hour + " (within window) -> clock-out triggered");
                launchClockAction(context, "out");
            } else {
                Log.i(TAG, "Exited office geofence outside clock-out window (hour=" + hour + ") -> ignored");
                eventLog.log("Geofence EXIT at hour=" + hour + " (outside window) -> ignored");
            }
        }
    }

    private void launchClockAction(Context context, String mode) {
        Intent actionIntent = new Intent(context, ClockActionActivity.class);
        actionIntent.putExtra(ClockActionActivity.EXTRA_MODE, mode);
        actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(actionIntent);
    }
}
