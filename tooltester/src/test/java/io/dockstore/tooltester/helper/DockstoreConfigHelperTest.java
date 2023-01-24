package io.dockstore.tooltester.helper;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;


/**
 * @author gluu
 * @since 05/12/17
 */
@ExtendWith(SystemStubsExtension.class)
public class DockstoreConfigHelperTest {

    @Test
    public void testCwltoolConfig() {
        final String url = "https://staging.dockstore.org:8443";
        String cwltoolConfig = DockstoreConfigHelper.getConfig(url, "cwltool");
        assertEquals("token: test\\nserver-url: https://staging.dockstore.org:8443\\ncwlrunner: cwltool\n", cwltoolConfig);
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
        assertEquals("token: test\\nserver-url: https://staging.dockstore.org:8443\\n# cromwell-version = 36\n", toilConfig);
    }

    @Test
    public void testCwlrunnerConfig() throws IOException {
        final String url = "https://staging.dockstore.org:8443";
        String toilConfig = DockstoreConfigHelper.getConfig(url, "cwl-runner");
        assertEquals("token: test\\nserver-url: https://staging.dockstore.org:8443\\ncwlrunner: cwl-runner\n", toilConfig);
    }

    @Test
    public void testPotatoConfig() throws Exception {
        final String url = "https://staging.dockstore.org:8443";
        int exitCode = catchSystemExit(() -> {
            DockstoreConfigHelper.getConfig(url, "potato");
        });
        assertEquals(ExceptionHandler.CLIENT_ERROR, exitCode);
    }
}
