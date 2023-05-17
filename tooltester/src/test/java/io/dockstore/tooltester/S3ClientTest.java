package io.dockstore.tooltester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.Test;

/**
 * @author gluu
 * @since 18/04/19
 */
public class S3ClientTest {

    @Test
    public void getToolTesterLogs() {

    }

    @Test
    public void getToolTesterLogsWithToolId() {

    }

    @Test
    public void getToolTesterLogsWithToolIdAndToolVersionName() {

    }

    @Test
    public void convertToolIdToPartialKey() throws UnsupportedEncodingException {
        String toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl";
        assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl", S3Client.convertToolIdToPartialKey(toolId));
        toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container";
        assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container", S3Client.convertToolIdToPartialKey(toolId));
        toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow";
        assertEquals("tool/quay.io/pancancer/pcawg-bwa-mem-workflow", S3Client.convertToolIdToPartialKey(toolId));
        toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow/thing";
        assertEquals("tool/quay.io/pancancer/pcawg-bwa-mem-workflow%2Fthing", S3Client.convertToolIdToPartialKey(toolId));
    }

}
