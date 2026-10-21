package com.hemanth.kekaautoclock;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs 60 seconds after a geofence ENTER, scheduled via WorkManager (survives the
 *  BroadcastReceiver returning). Re-checks location before actually clocking in, so a
 *  drive-by or GPS jitter near the office doesn't fire a false clock-in - matches how
 *  Timeero and similar commercial geofence-attendance apps confirm entries. */
public class ConfirmEntryWorker extends Worker {

    public ConfirmEntryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        EventLog eventLog = new EventLog(context);
        SettingsStore settings = new SettingsStore(context);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            eventLog.log("Clock-in confirmation skipped: no location permission - firing anyway");
            launchClockIn(context);
            return Result.success();
        }

        try {
            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build();
            Location location = Tasks.await(
                    client.getCurrentLocation(request, new CancellationTokenSource().getToken()),
                    20, TimeUnit.SECONDS);

            if (location == null) {
                eventLog.log("Clock-in confirmation: no fresh GPS fix available - firing anyway to be safe");
                launchClockIn(context);
                return Result.success();
            }

            Location office = new Location("office");
            office.setLatitude(settings.getLat());
            office.setLongitude(settings.getLng());
            float distance = location.distanceTo(office);

            if (distance <= settings.getRadius()) {
                eventLog.log("Clock-in confirmed after 60s (still " + (int) distance + "m from office) -> firing");
                launchClockIn(context);
            } else {
                eventLog.log("Clock-in NOT confirmed: left before the 60s confirmation window (now "
                        + (int) distance + "m away) -> skipped, no false clock-in");
            }
            return Result.success();

        } catch (ExecutionException | InterruptedException | TimeoutException | SecurityException e) {
            eventLog.log("Clock-in confirmation check failed (" + e.getClass().getSimpleName()
                    + ") - firing anyway to be safe");
            launchClockIn(context);
            return Result.success();
        }
    }

    private void launchClockIn(Context context) {
        Intent intent = new Intent(context, ClockActionActivity.class);
        intent.putExtra(ClockActionActivity.EXTRA_MODE, "in");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
