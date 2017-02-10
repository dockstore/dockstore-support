package io.dockstore.tooltester.client.cli;

import org.junit.Test;

/**
 * @author gluu
 * @since 10/02/17
 */
public class TimeHelperTest {
    @Test
    public void durationToString() throws Exception {
        System.out.println(TimeHelper.durationToString(Long.valueOf(3700000)));
        System.out.println(TimeHelper.durationToString(Long.valueOf(-2)));
    }

}