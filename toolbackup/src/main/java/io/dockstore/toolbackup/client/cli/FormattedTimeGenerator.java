package io.dockstore.toolbackup.client.cli;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Created by kcao on 11/01/17.
 */
public class FormattedTimeGenerator {
    private static final int MIN_IN_H = 60;

    public static String getFormattedTimeNow(LocalDateTime now) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }
    public static Date strToDate(String dateStr){
        try {
            return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(dateStr);
        } catch (ParseException e) {
            throw new RuntimeException("Could not parse: " + dateStr + " into a date");
        }
    }
    public static String elapsedTime(LocalDateTime start, LocalDateTime end) {
        long totalMinutes = ChronoUnit.MINUTES.between(start, end);
        int minutes = (int)totalMinutes % MIN_IN_H;
        int hours = ((int)totalMinutes - minutes)/MIN_IN_H;
        return hours + " hours and " + minutes + " minutes";
    }
}
