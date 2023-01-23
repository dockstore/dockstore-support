package io.dockstore.tooltester;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.Assertions;
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
        Assertions.assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl", S3Client.convertToolIdToPartialKey(toolId));
        toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container";
        Assertions.assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container", S3Client.convertToolIdToPartialKey(toolId));
        toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow";
        Assertions.assertEquals("tool/quay.io/pancancer/pcawg-bwa-mem-workflow", S3Client.convertToolIdToPartialKey(toolId));
        toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow/thing";
        Assertions.assertEquals("tool/quay.io/pancancer/pcawg-bwa-mem-workflow%2Fthing", S3Client.convertToolIdToPartialKey(toolId));
    }

}
