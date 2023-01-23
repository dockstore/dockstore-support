package io.dockstore.tooltester.client.cli;

import java.text.ParseException;
import java.util.Objects;

import io.dockstore.tooltester.helper.TimeHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author gluu
 * @since 10/02/17
 */
public class TimeHelperTest {
    @Test
    public void durationToString() throws Exception {
        String durationString = TimeHelper.durationToString(Long.valueOf(3700000));
        Assertions.assertTrue(durationString.equals("1h 2m"), "Incorrect time calculated, expected \"1h 2m\" but got " + durationString);
    }

    @Test
    public void dateFormatConvert() {
        String time = "2017-02-22T15:36:34.551+0000";
        try {
            time = TimeHelper.timeFormatConvert(time);
        } catch (ParseException e) {
            System.out.println("Could not convert date");
        }
        Assertions.assertTrue(Objects.equals(time, "2017-02-22 15:36"), "Incorrect date calculated, expected \"2017-02-22 15:36\" but got " + time);
    }

    @Test
    public void datetimeToEpoch() {
        String startTime = "2019-04-05T15:21:44.219+0000";
        String epochTimeString = TimeHelper.timeFormatToEpoch(startTime);
        Assertions.assertEquals("1554477704219", epochTimeString);
    }


}
