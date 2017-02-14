package io.dockstore.tooltester.client.cli;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 10/02/17
 */
class TimeHelper {
    static final double MILLISECONDSPERMINUTE = 60000;
    static final long MINUTESPERHOUR = 60;
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);

    static String durationToString(long millis) {
        try {
            double minutes = (double)millis / MILLISECONDSPERMINUTE;
            long roundedMinutes = (long)Math.ceil(millis / MILLISECONDSPERMINUTE) % MINUTESPERHOUR;
            long hours = TimeUnit.MILLISECONDS.toHours(millis);
            if (hours > 0) {
                return String.format("%dh %dm", hours, roundedMinutes);
            } else {
                return String.format("%dm", roundedMinutes);
            }
        } catch (Exception e) {
            LOG.info("Unable to get time");
            return "N/A";
        }
    }
}

