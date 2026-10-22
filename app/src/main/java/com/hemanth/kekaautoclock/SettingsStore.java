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

    private static final String KEY_LAST_TRIGGER_MILLIS = "last_trigger_millis";

    /** Debounce guard for GPS boundary-flapping, confirmed via real-device logs:
     *  5 separate ENTER events fired in 18 minutes while stationary near the office
     *  boundary, each spawning its own confirm-worker + WebView session for no reason. */
    public long getLastTriggerMillis() { return prefs.getLong(KEY_LAST_TRIGGER_MILLIS, 0); }
    public void setLastTriggerMillis(long millis) { prefs.edit().putLong(KEY_LAST_TRIGGER_MILLIS, millis).apply(); }

    public static String clickScript(String mode) {
        String text = mode.equals("in") ? "clock-in" : "clock-out";
        // Keka renders these two states with DIFFERENT element types AND
        // inconsistent capitalization: clock-out is a real
        // <button class="btn btn-danger">"Web Clock-out"</button>, clock-in
        // is an <a class="text-link">"Web Clock-In"</a> (capital I) styled
        // like the other action list items. Match either tag, case-insensitive.
        //
        // Self-polling instead of a single fixed delay: confirmed via real-device
        // testing that the page's hydration time varies run-to-run (worked at
        // ~13s one run, failed at 6s and even 11s another) - a fixed wait either
        // wastes time or isn't long enough. This polls every 500ms for up to 15s
        // and resolves (via the JS bridge directly) the instant it's ready,
        // instead of Java guessing a delay and reloading the whole page to retry.
        return "(function() { "
                + "const text = '" + text + "'; "
                + "const tryIn = (root) => { "
                + "  const els = root.querySelectorAll('button, a'); "
                + "  for (const el of els) { "
                + "    const t = (el.innerText || '').toLowerCase(); "
                + "    if (t.includes(text)) { el.click(); return true; } "
                + "  } "
                + "  return false; "
                + "}; "
                + "let attempts = 0; "
                + "const maxAttempts = 30; " // 30 * 500ms = 15s poll window
                + "const poll = () => { "
                + "  attempts++; "
                + "  const c = document.querySelector('employee-attendance-request-actions'); "
                + "  if (c && tryIn(c)) { window.AndroidBridge.onResult('done', document.title, location.href); return; } "
                + "  if (tryIn(document.body)) { window.AndroidBridge.onResult('done (fallback search)', document.title, location.href); return; } "
                + "  if (attempts >= maxAttempts) { "
                + "    const shadowCount = Array.from(document.querySelectorAll('*')).filter(el => el.shadowRoot).length; "
                + "    const sample = c ? Array.from(c.querySelectorAll('button, a')).map(el => (el.tagName + ':' + (el.innerText||'').trim()).slice(0,40)).join('|').slice(0,200) : 'n/a'; "
                + "    const diag = ' [diag: shadowRootElements=' + shadowCount + ', containerFound=' + !!c + ', attempts=' + attempts + ', buttons=' + sample + ']'; "
                + "    window.AndroidBridge.onResult((c ? 'no-op' : 'no-container') + diag, document.title, location.href); "
                + "    return; "
                + "  } "
                + "  setTimeout(poll, 500); "
                + "}; "
                + "poll(); "
                + "})()";
    }
}
