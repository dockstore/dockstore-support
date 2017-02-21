package io.dockstore.tooltester.helper;

import java.util.concurrent.TimeUnit;

/**
 * @author gluu
 * @since 10/02/17
 */
public class TimeHelper {
    private static final double MILLISECONDSPERMINUTE = 60000;
    private static final long MINUTESPERHOUR = 60;

    public static String durationToString(long millis) {
        double minutes = (double)millis / MILLISECONDSPERMINUTE;
        long roundedMinutes = (long)Math.ceil(minutes) % MINUTESPERHOUR;
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours > 0) {
            return String.format("%dh %dm", hours, roundedMinutes);
        } else {
            return String.format("%dm", roundedMinutes);
        }
    }
}

