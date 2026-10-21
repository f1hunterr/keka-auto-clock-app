package com.hemanth.kekaautoclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android clears registered geofences on reboot - this re-arms directly via
 *  GeofenceManager instead of opening MainActivity, because starting an Activity
 *  from a background BroadcastReceiver is blocked on Android 10+ and could
 *  silently fail to re-arm. Location permission is already granted from first
 *  setup, so no UI is needed here at all. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            new EventLog(context).log("Device rebooted - re-arming geofence");
            new GeofenceManager(context).register(null);
        }
    }
}
