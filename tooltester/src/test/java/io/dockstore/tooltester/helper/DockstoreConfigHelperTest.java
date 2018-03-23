package io.dockstore.tooltester.helper;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 05/12/17
 */
public class DockstoreConfigHelperTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testCwltoolConfig() {
        final String url = "https://staging.dockstore.org:8443";
        String cwltoolConfig = DockstoreConfigHelper.getConfig(url, "cwltool");
        assertEquals("token: test\\nserver-url: https://staging.dockstore.org:8443\\ncwlrunner: cwltool\n", cwltoolConfig);
    }

    @Test
    public void testRabixConfig() {
        final String url = "https://staging.dockstore.org:8443";
        String rabixConfig = DockstoreConfigHelper.getConfig(url, "bunny");
        assertEquals("token: test\\nserver-url: https://staging.dockstore.org:8443\\ncwlrunner: bunny\n", rabixConfig);
    }

    @Test
    public void testToilConfig() throws IOException {
        final String url = "https://staging.dockstore.org:8443";
        String toilConfig = DockstoreConfigHelper.getConfig(url, "toil");
        assertEquals("token: test\\nserver-url: https://staging.dockstore.org:8443\\ncwlrunner: toil\n", toilConfig);
    }

    @Test
    public void testCromwellConfig() throws IOException {
        final String url = "https://staging.dockstore.org:8443";
        String toilConfig = DockstoreConfigHelper.getConfig(url, "cromwell");
        assertEquals("token: test\\nserver-url: https://staging.dockstore.org:8443\\n", toilConfig);
    }

    @Test
    public void testCwlrunnerConfig() throws IOException {
        final String url = "https://staging.dockstore.org:8443";
        String toilConfig = DockstoreConfigHelper.getConfig(url, "cwl-runner");
        assertEquals("token: test\\nserver-url: https://staging.dockstore.org:8443\\ncwlrunner: cwl-runner\n", toilConfig);
    }

    @Test
    public void testPotatoConfig() {
        final String url = "https://staging.dockstore.org:8443";
        exit.expectSystemExitWithStatus(ExceptionHandler.CLIENT_ERROR);
        String rabixConfig = DockstoreConfigHelper.getConfig(url, "potato");
    }
}
