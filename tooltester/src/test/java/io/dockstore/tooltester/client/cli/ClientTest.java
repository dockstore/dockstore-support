package io.dockstore.tooltester.client.cli;

import java.io.File;

import com.beust.jcommander.ParameterException;
import io.dockstore.tooltester.helper.JenkinsHelper;
import io.dockstore.tooltester.helper.PipelineTester;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static io.dockstore.tooltester.client.cli.Client.main;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;

public class ClientTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    private Client client;

    @Before
    public void initialize() {
        this.client = new Client();
        this.client.setupClientEnvironment();
        Assert.assertNotNull("client API could not start", client.getContainersApi());
    }

    @Test
    public void setupEnvironment() {
        client = new Client();
        client.setupClientEnvironment();
        Assert.assertNotNull("client API could not start", client.getContainersApi());
    }

    /**
     * Tests if Jenkins server is set up
     */
    @Test
    public void setupTesters() {
        client.setupTesters();
        PipelineTester pipelineTester = client.getPipelineTester();
        Assert.assertNotNull("Jenkins server can not be reached", pipelineTester.getJenkins());
    }

    /**
     * Test with unknown command
     */
    @Test
    public void unknownCommand() {
        String[] argv = { "mmmrrrggglll" };
        exit.expectSystemExitWithStatus(COMMAND_ERROR);
        main(argv);
    }

    /**
     * Test enqueue which should run all jobs
     */
    @Test
    public void enqueue() {
        String[] argv = { "enqueue" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * Test enqueue with option --help which should display enqueue help
     */
    @Test
    public void enqueueHelp() {
        String[] argv = { "enqueue", "--help" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().contains("Test verified tools on Jenkins."));
    }

    /**
     * Test enqueue with a specific tool
     */
    @Test
    public void enqueueTool() {
        String[] argv = { "enqueue", "--tool", "quay.io/pancancer/pcawg_delly_workflow" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * Test enqueue with specific verified source
     */
    @Test
    public void enqueueToolSource() {
        String[] argv = { "enqueue", "--source", "Docktesters group" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Test
    public void createJenkinsTests() {
        String[] argv = { "sync" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * Test sync with option --help which should display sync help
     */
    @Test
    public void syncHelp() {
        String[] argv = { "sync", "--help" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().contains("Synchronizes with Jenkins to create tests for verified tools."));
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Test
    public void createJenkinsTestsSource() {
        String[] argv = { "sync", "--source", "Docktesters group"};
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Test
    public void createUnverifiedJenkinsTests() {
        String[] argv = { "sync", "--source", "Docktesters group", "--tools", "quay.io/ucsc_cgl/dockstore_tool_adtex" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * This tests the client with no parameters
     */
    @Test
    public void empty() {
        String[] argv = { "" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().contains("Usage: autotool [options] [command] [command options]"));
    }

    /**
     * This prints the usage for report
     */
    @Test
    public void report() {
        String[] argv = { "report" };
        main(argv);
        assertReport();
    }

    /**
     * This reports on a specific tool
     */
    @Test
    public void reportTool() {
        String[] argv = new String[] { "report", "--tool", "quay.io/briandoconnor/dockstore-tool-md5sum" };
        main(argv);
        assertReport();
    }

    /**
     * This reports on specific tools
     */
    @Test
    public void reportTools() {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa-mem-workflow",
                "quay.io/briandoconnor/dockstore-tool-md5sum" };
        main(argv);
        assertReport();

    }

    private void assertReport() {
        Assert.assertTrue(systemOutRule.getLog().contains("Tool/Workflow ID"));
        Assert.assertTrue(systemOutRule.getLog().contains("DATE"));
        Assert.assertTrue(systemOutRule.getLog().contains("Version"));
        Assert.assertTrue(systemOutRule.getLog().contains("Engine"));
        Assert.assertTrue(systemOutRule.getLog().contains("Action Performed"));
        Assert.assertTrue(systemOutRule.getLog().contains("Runtime"));
        Assert.assertTrue(systemOutRule.getLog().contains("Status of Test Files"));
        Assert.assertFalse(new File("tooltester/Report.csv").exists());
    }

    private void assertFileReport() {
        Assert.assertTrue(systemOutRule.getLog().contains("Build ID"));
        Assert.assertTrue(systemOutRule.getLog().contains("Tag"));
        Assert.assertTrue(systemOutRule.getLog().contains("File Name"));
        Assert.assertTrue(systemOutRule.getLog().contains("CWL ID"));
        Assert.assertTrue(systemOutRule.getLog().contains("md5sum"));
        Assert.assertTrue(systemOutRule.getLog().contains("File Size"));
        Assert.assertFalse(new File("tooltester/FileReport.csv").exists());
    }

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
        Assert.assertEquals("quay.io-pancancer-pcawg-bwa", JenkinsHelper.cleanSuffx(testSuffix1));
        Assert.assertEquals("-workflow-pancancer-pcawg-bwa", JenkinsHelper.cleanSuffx(testSuffix2));
    }

    /**
     * This tests file-report with no --tool option
     */
    @Test(expected = ParameterException.class)
    public void fileReport() {
        String[] argv = new String[] { "file-report" };
        main(argv);
    }

    /**
     * This tests file-report with no --tool option
     */
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
        Assert.assertTrue(
                systemOutRule.getLog().contains("Reports the file sizes and checksum of a verified tool across all tested versions."));
    }

    /**
     * This displays the help menu for the report command
     */
    @Test
    public void reportHelp() {
        String[] argv = { "report", "--help" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().contains("Report status of verified tools tested."));
    }

    /**
     * This displays the help menu for the main command
     */
    @Test
    public void mainHelp() {
        String[] argv = { "--help" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().contains("Usage: autotool [options] [command] [command options]"));
    }
}
