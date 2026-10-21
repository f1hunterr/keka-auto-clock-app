package com.hemanth.kekaautoclock;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

public class GeofenceManager {
    private static final String TAG = "KekaAutoClock";
    private final Context context;
    private final SettingsStore settings;

    public GeofenceManager(Context context) {
        this.context = context;
        this.settings = new SettingsStore(context);
    }

    public interface OnArmed {
        void onArmed();
    }

    public void register(OnArmed onArmed) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Location permission missing - cannot arm geofence", Toast.LENGTH_LONG).show();
            return;
        }

        Geofence officeGeofence = new Geofence.Builder()
                .setRequestId(SettingsStore.GEOFENCE_ID_OFFICE)
                .setCircularRegion(settings.getLat(), settings.getLng(), settings.getRadius())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay(30000)
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(officeGeofence)
                .build();

        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        GeofencingClient client = LocationServices.getGeofencingClient(context);
        try {
            client.addGeofences(request, pendingIntent)
                    .addOnSuccessListener(unused -> {
                        Log.i(TAG, "Geofence armed");
                        settings.setGeofenceArmed(true);
                        new EventLog(context).log("Geofence armed (lat=" + settings.getLat()
                                + " lng=" + settings.getLng() + " radius=" + settings.getRadius() + "m)");
                        if (onArmed != null) onArmed.onArmed();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to arm geofence", e);
                        settings.setGeofenceArmed(false);
                        new EventLog(context).log("FAILED to arm geofence: " + e.getMessage());
                        Toast.makeText(context, "Failed to arm geofence: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission for geofencing", e);
        }
    }
}
