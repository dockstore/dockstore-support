package io.dockstore.tooltester.client.cli;

import io.dockstore.tooltester.helper.TimeHelper;
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
        Assert.assertTrue("Incorret time calculated, expected \"1h 2m\" but got " + durationString, durationString.equals("1h 2m"));
    }

}