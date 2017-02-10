package io.dockstore.toolbackup.client.cli;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static java.lang.System.out;

/**
 * Created by kcao on 11/01/17.
 */
public class FormattedTimeGenerator {
    public static String getFormattedTimeNow(LocalDateTime now) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        return now.format(formatter);
    }
    public static Date strToDate(String dateStr){
        try {
            return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(dateStr);
        } catch (ParseException e) {
            throw new RuntimeException("Could not parse: " + dateStr + " into a date");
        }
    }
    public static void elapsedTime(LocalDateTime start, LocalDateTime end) {
        long minutes = ChronoUnit.MINUTES.between(start, end);
        long hours = ChronoUnit.HOURS.between(start, end);
        out.println("Back-up script completed successfully in " + hours + " hours and " + minutes + " minutes");
    }
}
