package io.dockstore.metricsaggregator.client.cli;

import static io.dockstore.metricsaggregator.client.cli.TerraMetricsSubmitter.formatStringInIso8601Date;
import static io.dockstore.metricsaggregator.client.cli.TerraMetricsSubmitter.getExecutionTime;
import static io.dockstore.metricsaggregator.client.cli.TerraMetricsSubmitter.getSourceUrlComponents;
import static io.dockstore.metricsaggregator.client.cli.TerraMetricsSubmitter.makePathAbsolute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerraMetricsSubmitterTest {

    @Test
    void testFormatStringInIso8601Date() {
        assertEquals("2022-07-15T15:37:06.123456Z", formatStringInIso8601Date("2022-07-15 15:37:06.123456 UTC").get());
        assertEquals("2022-07-15T15:37:06.450Z", formatStringInIso8601Date("2022-07-15 15:37:06.450000 UTC").get());
        assertEquals("2022-06-30T00:33:44.101Z", formatStringInIso8601Date("2022-06-30 00:33:44.101000 UTC").get());
        assertEquals("2022-09-06T13:46:53Z", formatStringInIso8601Date("2022-09-06 13:46:53").get());
        assertEquals("2022-05-25T13:13:08.510Z", formatStringInIso8601Date("2022-05-25 13:13:08.51 UTC").get());
        assertEquals("2022-12-01T01:52:05.700Z", formatStringInIso8601Date("2022-12-01 01:52:05.7 UTC").get());
    }

    @Test
    void testGetExecutionStatusEnumFromTerraStatus() {
        assertEquals(ExecutionStatusEnum.SUCCESSFUL, TerraMetricsSubmitter.getExecutionStatusEnumFromTerraStatus("Succeeded").get());
        assertEquals(ExecutionStatusEnum.FAILED, TerraMetricsSubmitter.getExecutionStatusEnumFromTerraStatus("Failed").get());
        assertEquals(ExecutionStatusEnum.ABORTED, TerraMetricsSubmitter.getExecutionStatusEnumFromTerraStatus("Aborted").get());
        // These two statuses are present in the workflow executions Terra provided, but they don't represent a completed execution
        assertTrue(TerraMetricsSubmitter.getExecutionStatusEnumFromTerraStatus("Aborting").isEmpty());
        assertTrue(TerraMetricsSubmitter.getExecutionStatusEnumFromTerraStatus("Running").isEmpty());
    }

    @Test
    void testGetExecutionTime() {
        assertEquals("PT100M", getExecutionTime("100").get());
        assertTrue(getExecutionTime("").isEmpty());
        assertTrue(getExecutionTime(" ").isEmpty());
        assertTrue(getExecutionTime(null).isEmpty());
    }

    @Test
    void testGetSourceUrlComponents() {
        assertEquals(List.of("theiagen", "public_health_viral_genomics", "v2.0.0", "workflows", "wf_theiacov_fasta.wdl"), getSourceUrlComponents("https://raw.githubusercontent.com/theiagen/public_health_viral_genomics/v2.0.0/workflows/wf_theiacov_fasta.wdl"));
        // This source_url has consecutive slashes
        assertEquals(List.of("theiagen", "public_health_viral_genomics", "v2.0.0", "workflows", "wf_theiacov_fasta.wdl"), getSourceUrlComponents("https://raw.githubusercontent.com/theiagen/public_health_viral_genomics/v2.0.0//workflows/wf_theiacov_fasta.wdl"));
        assertEquals(List.of(), getSourceUrlComponents("https://nottherawgithuburlprefix/theiagen/public_health_viral_genomics/v2.0.0//workflows/wf_theiacov_fasta.wdl"));
    }

    @Test
    void testMakePathAbsolute() {
        assertEquals("/foo.wdl", makePathAbsolute("foo.wdl"));
        assertEquals("/foo.wdl", makePathAbsolute("/foo.wdl"));
    }
}
