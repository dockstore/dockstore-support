package io.dockstore.tooltester.client.cli;

<<<<<<< HEAD
import java.text.ParseException;
import java.util.Objects;

import io.dockstore.tooltester.helper.TimeHelper;
<<<<<<< HEAD
=======
>>>>>>> d7e8c79... Feature/jenkins example (#5)
=======
>>>>>>> d889a5a... Logger not avail at compile time fix.
import org.junit.Assert;
import org.junit.Test;

/**
 * @author gluu
 * @since 10/02/17
 */
public class TimeHelperTest {
    @Test
    public void durationToString() throws Exception {
        String durationString = TimeHelper.durationToString(Long.valueOf(3700000));
<<<<<<< HEAD
        Assert.assertTrue("Incorrect time calculated, expected \"1h 2m\" but got " + durationString, durationString.equals("1h 2m"));
    }

    @Test
    public void dateFormatConvert() {
        String time = "2017-02-22T15:36:34.551+0000";
        try {
            time = TimeHelper.timeFormatConvert(time);
        } catch (ParseException e) {
            System.out.println("Could not convert date");
        }
        Assert.assertTrue("Incorrect date calculated, expected \"2017-02-22 15:36\" but got " + time,
                Objects.equals(time, "2017-02-22 15:36"));
=======
        Assert.assertTrue("Incorret time calculated, expected \"1h 2m\" but got " + durationString, durationString.equals("1h 2m"));
>>>>>>> d7e8c79... Feature/jenkins example (#5)
    }

}
