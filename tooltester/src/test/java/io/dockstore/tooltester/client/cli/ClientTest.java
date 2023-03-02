package io.dockstore.tooltester.client.cli;

import java.io.File;

import com.beust.jcommander.ParameterException;
import io.dockstore.tooltester.helper.JenkinsHelper;
import io.dockstore.tooltester.helper.PipelineTester;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.tooltester.client.cli.Client.main;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * Many tests ignored due to reasons explained in this PR https://github.com/dockstore/dockstore-support/pull/448
 */

@ExtendWith(SystemStubsExtension.class)
public class ClientTest {
    @SystemStub
    private SystemOut systemOut;

    @SystemStub
    private SystemErr systemErr;

    private Client client;

    @BeforeEach
    public void initialize() {
        this.client = new Client();
        this.client.setupClientEnvironment();
        assertNotNull(client.getContainersApi(), "client API could not start");
    }

    @Test
    public void setupEnvironment() {
        client = new Client();
        client.setupClientEnvironment();
        assertNotNull(client.getContainersApi(), "client API could not start");
    }

    /**
     * Tests if Jenkins server is set up
     */
    @Disabled
    @Test
    public void setupTesters() {
        client.setupTesters();
        PipelineTester pipelineTester = client.getPipelineTester();
        assertNotNull(pipelineTester.getJenkins(), "Jenkins server can not be reached");
    }

    /**
     * Test with unknown command
     */
    @Test
    public void unknownCommand() throws Exception {
        String[] argv = { "mmmrrrggglll" };
        int exitCode = catchSystemExit(() -> {
            main(argv);
        });
        assertEquals(COMMAND_ERROR, exitCode);
    }

    /**
     * Test enqueue which should run all jobs
     */
    @Disabled
    @Test
    public void enqueue() throws InterruptedException {
        String[] argv = { "enqueue" };
        main(argv);
        assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * Test enqueue with option --help which should display enqueue help
     */
    @Test
    public void enqueueHelp() throws InterruptedException {
        String[] argv = { "enqueue", "--help" };
        main(argv);
        assertTrue(systemOut.getText().contains("Test verified tools on Jenkins."));
    }

    /**
     * Test enqueue with a specific tool
     */
    @Disabled
    @Test
    public void enqueueTool() throws InterruptedException {
        String[] argv = { "enqueue", "--tool", "quay.io/pancancer/pcawg_delly_workflow" };
        main(argv);
        assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * Test enqueue with specific verified source
     */
    @Disabled
    @Test
    public void enqueueToolSource() throws InterruptedException {
        String[] argv = { "enqueue", "--source", "Docktesters group" };
        main(argv);
        assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Disabled
    @Test
    public void createJenkinsTests() throws InterruptedException {
        String[] argv = { "sync" };
        main(argv);
        assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * Test sync with option --help which should display sync help
     */
    @Test
    public void syncHelp() throws InterruptedException {
        String[] argv = { "sync", "--help" };
        main(argv);
        assertTrue(systemOut.getText().contains("Synchronizes with Jenkins to create tests for verified tools."));
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Disabled
    @Test
    public void createJenkinsTestsSource() throws InterruptedException {
        String[] argv = { "sync", "--source", "Docktesters group"};
        main(argv);
        assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Disabled
    @Test
    public void createUnverifiedJenkinsTests() throws InterruptedException {
        String[] argv = { "sync", "--source", "Docktesters group", "--tools", "quay.io/ucsc_cgl/dockstore_tool_adtex" };
        main(argv);
        assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * This tests the client with no parameters
     */
    @Test
    public void emptyOne() throws InterruptedException {
        String[] argv = { "" };
        main(argv);
        assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
     }

    @Test
    public void emptyTwo() throws InterruptedException {
        String[] argv = { };
        main(argv);
        assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
    }

    /**
     * This prints the usage for report
     */
    @Disabled
    @Test
    public void report() throws InterruptedException {
        String[] argv = { "report" };
        main(argv);
        assertReport();
    }

    /**
     * This reports on a specific tool
     */
    @Disabled
    @Test
    public void reportTool() throws InterruptedException {
        String[] argv = new String[] { "report", "--tool", "quay.io/briandoconnor/dockstore-tool-md5sum" };
        main(argv);
        assertReport();
    }

    /**
     * This reports on specific tools
     */
    @Disabled
    @Test
    public void reportTools() throws InterruptedException {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa-mem-workflow",
                "quay.io/briandoconnor/dockstore-tool-md5sum" };
        main(argv);
        assertReport();

    }

    private void assertReport() {
        assertTrue(systemOut.getText().contains("Tool/Workflow ID"));
        assertTrue(systemOut.getText().contains("DATE"));
        assertTrue(systemOut.getText().contains("Version"));
        assertTrue(systemOut.getText().contains("Engine"));
        assertTrue(systemOut.getText().contains("Action Performed"));
        assertTrue(systemOut.getText().contains("Runtime"));
        assertTrue(systemOut.getText().contains("Status of Test Files"));
        assertFalse(new File("tooltester/Report.csv").exists());
    }

    private void assertFileReport() {
        assertTrue(systemOut.getText().contains("Build ID"));
        assertTrue(systemOut.getText().contains("Tag"));
        assertTrue(systemOut.getText().contains("File Name"));
        assertTrue(systemOut.getText().contains("CWL ID"));
        assertTrue(systemOut.getText().contains("md5sum"));
        assertTrue(systemOut.getText().contains("File Size"));
        assertFalse(new File("tooltester/FileReport.csv").exists());
    }

    @Disabled
    @Test
    public void reportInvalidTool() throws InterruptedException {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa" };
        main(argv);
        assertReport();
    }

    @Test
    public void testCleanSuffix() {
        final String testSuffix1 = "quay.io/pancancer/pcawg-bwa";
        final String testSuffix2 = "#workflow/pancancer/pcawg-bwa";
        assertEquals("quay.io-pancancer-pcawg-bwa", JenkinsHelper.cleanSuffx(testSuffix1));
        assertEquals("-workflow-pancancer-pcawg-bwa", JenkinsHelper.cleanSuffx(testSuffix2));
    }

    /**
     * This tests file-report with no --tool option
     */
    @Test
    public void fileReport() {
        String[] argv = new String[] { "file-report" };
        assertThrows(ParameterException.class, () -> main(argv));
    }

    /**
     * This tests file-report with no --tool option
     */
    @Disabled
    @Test
    public void fileReportTool() throws InterruptedException {
        String[] argv = new String[] { "file-report", "--tool", "quay.io/briandoconnor/dockstore-tool-md5sum" };
        main(argv);
        assertFileReport();
    }

    /**
     * This tests the help message for the file-report command
     */
    @Test
    public void fileReportHelp() throws InterruptedException {
        String[] argv = new String[] { "file-report", "--help" };
        main(argv);
        assertTrue(
                systemOut.getText().contains("Reports the file sizes and checksum of a verified tool across all tested versions."));
    }

    /**
     * This displays the help menu for the report command
     */
    @Test
    public void reportHelp() throws InterruptedException {
        String[] argv = { "report", "--help" };
        main(argv);
        assertTrue(systemOut.getText().contains("Report status of verified tools tested."));
    }

    /**
     * This displays the help menu for the main command
     */
    @Test
    public void mainHelp() throws InterruptedException {
        String[] argv = { "--help" };
        main(argv);
        assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
    }
}
