package io.dockstore.tooltester.client.cli;

import java.io.File;

import com.beust.jcommander.ParameterException;
import io.dockstore.tooltester.helper.JenkinsHelper;
import io.dockstore.tooltester.helper.PipelineTester;
import org.junit.jupiter.api.Assertions;
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
        Assertions.assertNotNull(client.getContainersApi(), "client API could not start");
    }

    @Test
    public void setupEnvironment() {
        client = new Client();
        client.setupClientEnvironment();
        Assertions.assertNotNull(client.getContainersApi(), "client API could not start");
    }

    /**
     * Tests if Jenkins server is set up
     */
    @Disabled
    @Test
    public void setupTesters() {
        client.setupTesters();
        PipelineTester pipelineTester = client.getPipelineTester();
        Assertions.assertNotNull(pipelineTester.getJenkins(), "Jenkins server can not be reached");
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
        Assertions.assertEquals(COMMAND_ERROR, exitCode);
    }

    /**
     * Test enqueue which should run all jobs
     */
    @Disabled
    @Test
    public void enqueue() {
        String[] argv = { "enqueue" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * Test enqueue with option --help which should display enqueue help
     */
    @Test
    public void enqueueHelp() {
        String[] argv = { "enqueue", "--help" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().contains("Test verified tools on Jenkins."));
    }

    /**
     * Test enqueue with a specific tool
     */
    @Disabled
    @Test
    public void enqueueTool() {
        String[] argv = { "enqueue", "--tool", "quay.io/pancancer/pcawg_delly_workflow" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * Test enqueue with specific verified source
     */
    @Disabled
    @Test
    public void enqueueToolSource() {
        String[] argv = { "enqueue", "--source", "Docktesters group" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Disabled
    @Test
    public void createJenkinsTests() {
        String[] argv = { "sync" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * Test sync with option --help which should display sync help
     */
    @Test
    public void syncHelp() {
        String[] argv = { "sync", "--help" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().contains("Synchronizes with Jenkins to create tests for verified tools."));
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Disabled
    @Test
    public void createJenkinsTestsSource() {
        String[] argv = { "sync", "--source", "Docktesters group"};
        main(argv);
        Assertions.assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Disabled
    @Test
    public void createUnverifiedJenkinsTests() {
        String[] argv = { "sync", "--source", "Docktesters group", "--tools", "quay.io/ucsc_cgl/dockstore_tool_adtex" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().isEmpty());
    }

    /**
     * This tests the client with no parameters
     */
    @Test
    public void emptyOne() {
        String[] argv = { "" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
     }

    @Test
    public void emptyTwo() {
        String[] argv = { };
        main(argv);
        Assertions.assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
    }

    /**
     * This prints the usage for report
     */
    @Disabled
    @Test
    public void report() {
        String[] argv = { "report" };
        main(argv);
        assertReport();
    }

    /**
     * This reports on a specific tool
     */
    @Disabled
    @Test
    public void reportTool() {
        String[] argv = new String[] { "report", "--tool", "quay.io/briandoconnor/dockstore-tool-md5sum" };
        main(argv);
        assertReport();
    }

    /**
     * This reports on specific tools
     */
    @Disabled
    @Test
    public void reportTools() {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa-mem-workflow",
                "quay.io/briandoconnor/dockstore-tool-md5sum" };
        main(argv);
        assertReport();

    }

    private void assertReport() {
        Assertions.assertTrue(systemOut.getText().contains("Tool/Workflow ID"));
        Assertions.assertTrue(systemOut.getText().contains("DATE"));
        Assertions.assertTrue(systemOut.getText().contains("Version"));
        Assertions.assertTrue(systemOut.getText().contains("Engine"));
        Assertions.assertTrue(systemOut.getText().contains("Action Performed"));
        Assertions.assertTrue(systemOut.getText().contains("Runtime"));
        Assertions.assertTrue(systemOut.getText().contains("Status of Test Files"));
        Assertions.assertFalse(new File("tooltester/Report.csv").exists());
    }

    private void assertFileReport() {
        Assertions.assertTrue(systemOut.getText().contains("Build ID"));
        Assertions.assertTrue(systemOut.getText().contains("Tag"));
        Assertions.assertTrue(systemOut.getText().contains("File Name"));
        Assertions.assertTrue(systemOut.getText().contains("CWL ID"));
        Assertions.assertTrue(systemOut.getText().contains("md5sum"));
        Assertions.assertTrue(systemOut.getText().contains("File Size"));
        Assertions.assertFalse(new File("tooltester/FileReport.csv").exists());
    }

    @Disabled
    @Test
    public void reportInvalidTool() {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa" };
        main(argv);
        assertReport();
    }

    @Test
    public void testCleanSuffix() {
        final String testSuffix1 = "quay.io/pancancer/pcawg-bwa";
        final String testSuffix2 = "#workflow/pancancer/pcawg-bwa";
        Assertions.assertEquals("quay.io-pancancer-pcawg-bwa", JenkinsHelper.cleanSuffx(testSuffix1));
        Assertions.assertEquals("-workflow-pancancer-pcawg-bwa", JenkinsHelper.cleanSuffx(testSuffix2));
    }

    /**
     * This tests file-report with no --tool option
     */
    @Test
    public void fileReport() {
        String[] argv = new String[] { "file-report" };
        Assertions.assertThrows(ParameterException.class, () -> main(argv));
    }

    /**
     * This tests file-report with no --tool option
     */
    @Disabled
    @Test
    public void fileReportTool() {
        String[] argv = new String[] { "file-report", "--tool", "quay.io/briandoconnor/dockstore-tool-md5sum" };
        main(argv);
        assertFileReport();
    }

    /**
     * This tests the help message for the file-report command
     */
    @Test
    public void fileReportHelp() {
        String[] argv = new String[] { "file-report", "--help" };
        main(argv);
        Assertions.assertTrue(
                systemOut.getText().contains("Reports the file sizes and checksum of a verified tool across all tested versions."));
    }

    /**
     * This displays the help menu for the report command
     */
    @Test
    public void reportHelp() {
        String[] argv = { "report", "--help" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().contains("Report status of verified tools tested."));
    }

    /**
     * This displays the help menu for the main command
     */
    @Test
    public void mainHelp() {
        String[] argv = { "--help" };
        main(argv);
        Assertions.assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
    }
}
