package io.dockstore.tooltester.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.tooltester.helper.TimeHelper;
import java.text.ParseException;
import org.junit.jupiter.api.Test;

/**
 * @author gluu
 * @since 10/02/17
 */
public class TimeHelperTest {
    @Test
    void durationToString() {
        String durationString = TimeHelper.durationToString(3700000L);
        assertEquals("1h 2m", durationString, "Incorrect time calculated, expected \"1h 2m\" but got " + durationString);
    }

    @Test
    void dateFormatConvert() {
        String time = "2017-02-22T15:36:34.551+0000";
        try {
            time = TimeHelper.timeFormatConvert(time);
        } catch (ParseException e) {
            System.out.println("Could not convert date");
        }
        assertEquals("2017-02-22 15:36", time, "Incorrect date calculated, expected \"2017-02-22 15:36\" but got " + time);
    }

    @Test
    void datetimeToEpoch() {
        String startTime = "2019-04-05T15:21:44.219+0000";
        String epochTimeString = TimeHelper.timeFormatToEpoch(startTime);
        assertEquals("1554477704219", epochTimeString);
    }


}
