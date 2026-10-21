package com.hemanth.kekaautoclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android clears registered geofences on reboot - this re-opens MainActivity
 *  briefly to re-register them. MainActivity's permission checks are already
 *  satisfied at this point (granted once at first setup), so this is silent. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        }
    }
}
