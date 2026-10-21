package com.hemanth.kekaautoclock;

/** All the tunables in one place — office location, times, Keka URL. */
public final class Config {
    private Config() {}

    public static final double OFFICE_LAT = 12.973844598355516;
    public static final double OFFICE_LNG = 77.64715826025561;
    public static final float OFFICE_RADIUS_M = 100f;

    public static final String KEKA_URL =
            "https://houseofbluebeans.keka.com/#/me/attendance/logs";

    // Clock-out only fires inside this window (24h clock), guarding against
    // a stray overnight GPS blip triggering an accidental clock-out.
    public static final int CLOCKOUT_START_HOUR = 18; // 18:00
    public static final int CLOCKOUT_END_HOUR = 24;   // up to 23:59

    public static final String GEOFENCE_ID_OFFICE = "office";

    // JS payload shared by both directions; the "mode" param picks clock-in or clock-out.
    public static String clickScript(String mode) {
        String text = mode.equals("in") ? "Clock-in" : "Clock-out";
        return "(() => { "
                + "const c = document.querySelector('employee-attendance-request-actions'); "
                + "const b = c && c.querySelector('button'); "
                + "if (b && b.innerText.includes('" + text + "')) { b.click(); return 'done'; } "
                + "return 'no-op'; })()";
    }
}
