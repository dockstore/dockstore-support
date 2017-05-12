package io.dockstore.tooltester.helper;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * @author gluu
 * @since 10/02/17
 */
public class TimeHelper {
    private static final double MILLISECONDS_PER_MINUTE = 60000;
    private static final long MINUTES_PER_HOUR = 60;

    /**
     * Generates prefix for the report name
     *
     * @return the prefix for the report names based on the current date and time
     */
    public static String getDateFilePrefix() {
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        // Incrementing calendar month by 1 because it starts from 0
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return year + "-" + month + "-" + day + "-";
    }

    public static String durationToString(long millis) {
        double minutes = (double)millis / MILLISECONDS_PER_MINUTE;
        long roundedMinutes = (long)Math.ceil(minutes) % MINUTES_PER_HOUR;
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

