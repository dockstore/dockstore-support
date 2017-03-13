package io.dockstore.tooltester.helper;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
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
        if (!(roundedMinutes > 0)) {
            System.out.print(millis);
        }
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours > 0) {
            return String.format("%dh %dm", hours, roundedMinutes);
        } else {
            return String.format("%dm", roundedMinutes);
        }
    }

    public static String timeFormatConvert(String time) throws ParseException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");
        Date result;
        result = df.parse(time);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(result);
    }

    public static String getDurationSinceDate(String date) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");
        Date result;
        Long diffInMillies;
        String duration = null;
        try {
            result = df.parse(date);

            diffInMillies = new Date().getTime() - result.getTime();
            duration = durationToString(diffInMillies);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return duration;
    }
}

