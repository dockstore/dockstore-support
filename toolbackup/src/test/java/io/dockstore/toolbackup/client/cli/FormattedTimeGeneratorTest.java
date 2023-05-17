package io.dockstore.toolbackup.client.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Date;
import org.junit.Test;

/**
 * Created by kcao on 17/02/17.
 */
public class FormattedTimeGeneratorTest {
    @Test
    public void getFormattedTimeNow() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        //assertTrue(now.toString().contains(FormattedTimeGenerator.getFormattedTimeNow(now)));
        String[] parts = FormattedTimeGenerator.getFormattedTimeNow(now).split(" ");
        String nowStr = now.toString();
        assertTrue(nowStr.contains(parts[0]) && nowStr.contains(parts[1]));
    }

    @Test
    public void strToDate() throws Exception {
        // Fri Mar 03 03:03:03 EST 2017
        String regex = "^(Fri Mar 03 03:03:03)(\\s)(\\w{3})(\\s)(2017)$";
        String dateStr = "03-03-2017 03:03:03";
        Date date = FormattedTimeGenerator.strToDate(dateStr);
        assertTrue(date.toString().matches(regex));
    }

    @Test
    public void elapsedTime() throws Exception {
        LocalDateTime begin = LocalDateTime.of(2017, Month.FEBRUARY, 18, 3, 30, 0);
        LocalDateTime end = LocalDateTime.of(2017, Month.FEBRUARY, 18, 10, 36, 30);

        String durationMsg = FormattedTimeGenerator.elapsedTime(begin, end);

        assertEquals("7 hours and 6 minutes", durationMsg.toString());
    }
}
