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
        if (event == null || event.hasError()) {
            Log.e(TAG, "Geofencing event error: " + (event != null ? event.getErrorCode() : "null event"));
            return;
        }

        int transition = event.getGeofenceTransition();

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.i(TAG, "Entered office geofence -> clock-in");
            launchClockAction(context, "in");

        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            if (hour >= Config.CLOCKOUT_START_HOUR && hour < Config.CLOCKOUT_END_HOUR) {
                Log.i(TAG, "Exited office geofence within clock-out window -> clock-out");
                launchClockAction(context, "out");
            } else {
                Log.i(TAG, "Exited office geofence outside clock-out window (hour=" + hour + ") -> ignored");
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
