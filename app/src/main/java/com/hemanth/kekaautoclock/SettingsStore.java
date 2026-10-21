package com.hemanth.kekaautoclock;

import android.content.Context;
import android.content.SharedPreferences;

/** Persisted, user-editable settings (replaces the old hardcoded Config constants).
 *  Falls back to the original coordinates/window as defaults on first run. */
public final class SettingsStore {
    private static final String PREFS = "keka_settings";

    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";
    private static final String KEY_RADIUS = "radius";
    private static final String KEY_CLOCKOUT_START = "clockout_start";
    private static final String KEY_CLOCKOUT_END = "clockout_end";
    private static final String KEY_GEOFENCE_ARMED = "geofence_armed";
    private static final String KEY_LAST_EVENT = "last_event";

    // Original defaults
    public static final double DEFAULT_LAT = 12.973844598355516;
    public static final double DEFAULT_LNG = 77.64715826025561;
    public static final float DEFAULT_RADIUS = 100f;
    public static final int DEFAULT_CLOCKOUT_START = 18;
    public static final int DEFAULT_CLOCKOUT_END = 24;

    public static final String KEKA_URL =
            "https://houseofbluebeans.keka.com/#/me/attendance/logs";
    public static final String GEOFENCE_ID_OFFICE = "office";

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public double getLat() { return Double.longBitsToDouble(prefs.getLong(KEY_LAT, Double.doubleToLongBits(DEFAULT_LAT))); }
    public double getLng() { return Double.longBitsToDouble(prefs.getLong(KEY_LNG, Double.doubleToLongBits(DEFAULT_LNG))); }
    public float getRadius() { return prefs.getFloat(KEY_RADIUS, DEFAULT_RADIUS); }
    public int getClockoutStartHour() { return prefs.getInt(KEY_CLOCKOUT_START, DEFAULT_CLOCKOUT_START); }
    public int getClockoutEndHour() { return prefs.getInt(KEY_CLOCKOUT_END, DEFAULT_CLOCKOUT_END); }
    public boolean isGeofenceArmed() { return prefs.getBoolean(KEY_GEOFENCE_ARMED, false); }
    public String getLastEvent() { return prefs.getString(KEY_LAST_EVENT, "No events yet"); }

    public void setLocation(double lat, double lng, float radius) {
        prefs.edit()
                .putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LNG, Double.doubleToLongBits(lng))
                .putFloat(KEY_RADIUS, radius)
                .apply();
    }

    public void setClockoutWindow(int startHour, int endHour) {
        prefs.edit()
                .putInt(KEY_CLOCKOUT_START, startHour)
                .putInt(KEY_CLOCKOUT_END, endHour)
                .apply();
    }

    public void setGeofenceArmed(boolean armed) {
        prefs.edit().putBoolean(KEY_GEOFENCE_ARMED, armed).apply();
    }

    public void setLastEvent(String description) {
        prefs.edit().putString(KEY_LAST_EVENT, description).apply();
    }

    public static String clickScript(String mode) {
        String text = mode.equals("in") ? "clock-in" : "clock-out";
        // Keka renders these two states with DIFFERENT element types AND
        // inconsistent capitalization: clock-out is a real
        // <button class="btn btn-danger">"Web Clock-out"</button>, clock-in
        // is an <a class="text-link">"Web Clock-In"</a> (capital I) styled
        // like the other action list items. Match either tag, case-insensitive.
        return "(() => { "
                + "const c = document.querySelector('employee-attendance-request-actions'); "
                + "if (!c) return 'no-container'; "
                + "const els = c.querySelectorAll('button, a'); "
                + "for (const el of els) { "
                + "  const t = (el.innerText || '').toLowerCase(); "
                + "  if (t.includes('" + text + "')) { el.click(); return 'done'; } "
                + "} "
                + "return 'no-op'; })()";
    }
}
