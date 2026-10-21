package com.hemanth.kekaautoclock;

import java.util.Locale;

/** Small shared helper so hour-of-day ints (used internally for the
 *  clock-out window logic, matches Calendar.HOUR_OF_DAY) display as
 *  12-hour clock with AM/PM instead of raw 0-23. */
public final class TimeUtils {
    private TimeUtils() {}

    public static String formatHour12(int hourOfDay) {
        int h = hourOfDay % 24;
        String amPm = h < 12 ? "AM" : "PM";
        int displayHour = h % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format(Locale.US, "%d:00 %s", displayHour, amPm);
    }
}
