package io.dockstore.toolbackup.client.cli;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Created by kcao on 11/01/17.
 */
public class FormattedTimeGenerator {
    public static String getFormattedTimeNow() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        return now.format(formatter);
    }
    public static Date strToDate(String dateStr){
        try {
            return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(dateStr);
        } catch (ParseException e) {
            throw new RuntimeException("Could not parse: " + dateStr + " into a date");
        }
    }
}
