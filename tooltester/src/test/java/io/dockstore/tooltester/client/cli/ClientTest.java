package io.dockstore.tooltester.client.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.beust.jcommander.ParameterException;
import com.offbytwo.jenkins.JenkinsServer;
import io.dockstore.tooltester.helper.PipelineTester;
import io.swagger.client.ApiException;
import io.swagger.client.model.Tool;
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
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    @Test
    public void setupEnvironment() throws Exception {
        client = new Client();
        client.setupClientEnvironment();
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    /**
     * Tests if Jenkins server is set up
     */
    @Test
    public void setupTesters() {
        client.setupTesters();
        PipelineTester pipelineTester = client.getPipelineTester();
        Assert.assertTrue("Jenkins server can not be reached", pipelineTester.getJenkins() != null);
    }

    /**
     * This is for admin use only.  It deletes all created Jenkins pipelines
     */
//    public void deleteJenkinsTests() {
//        client.setupTesters();
//        PipelineTester pipelineTester = client.getPipelineTester();
//        JenkinsServer jenkins = pipelineTester.getJenkins();
//        Assert.assertTrue("Jenkins server can not be reached", jenkins != null);
//        pipelineTester.deleteJobs("DockerfileTest");
//        pipelineTester.deleteJobs("ParameterFileTest");
//        pipelineTester.deleteJobs("PipelineTest");
//    }

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
     * Test enqueue with no parameters which should print the help usage
     */
    @Test
    public void enqueue() {
        String[] argv = { "enqueue" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().contains("Test verified tools on Jenkins."));

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
     * Test enqueue with option --all which should run all jobs
     */
    @Test
    public void enqueueAll() {
        String[] argv = { "enqueue", "--all" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * Test enqueue with default options
     */
    @Test
    public void enqueueTool() {
        String[] argv = { "enqueue", "--tool", "quay.io/pancancer/pcawg-bwa-mem-workflow" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * Test enqueue with default options
     */
    @Test
    public void enqueueUnverifiedTool() {
        String[] argv = { "enqueue", "--unverified-tool", "quay.io/jeltje/adtex" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Test
    public void createJenkinsTests() {
        String[] argv = { "sync", "--execution", "jenkins", "--source", "Docktesters group", "--api",
                "https://www.dockstore.org:8443/api/ga4gh/v1" };
        main(argv);
        Assert.assertTrue(systemOutRule.getLog().isEmpty());
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Test
    public void createUnverifiedJenkinsTests() {
        String[] argv = { "sync", "--execution", "jenkins", "--source", "Docktesters group", "--api",
                "https://www.dockstore.org:8443/api/ga4gh/v1", "--unverified-tool", "quay.io/jeltje/adtex" };
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
     * This gets the report of all the tools
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
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa-mem-workflow" };
        main(argv);
        assertReport();
    }

    /**
     * This reports on specific tools
     */
    @Test
    public void reportTools() {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa-mem-workflow" };
        main(argv);
        assertReport();

    }

    private void assertReport() {
        Assert.assertTrue(systemOutRule.getLog().contains("Tool/Workflow ID"));
        Assert.assertTrue(systemOutRule.getLog().contains("DATE"));
        Assert.assertTrue(systemOutRule.getLog().contains("Version"));
        Assert.assertTrue(systemOutRule.getLog().contains("Location of testing"));
        Assert.assertTrue(systemOutRule.getLog().contains("Action Performed"));
        Assert.assertTrue(systemOutRule.getLog().contains("Runtime"));
        Assert.assertTrue(systemOutRule.getLog().contains("Status of Test Files"));
        Assert.assertTrue(!new File("tooltester/Report.csv").exists());
    }

    private void assertFileReport() {
        Assert.assertTrue(systemOutRule.getLog().contains("Build ID"));
        Assert.assertTrue(systemOutRule.getLog().contains("Tag"));
        Assert.assertTrue(systemOutRule.getLog().contains("File Name"));
        Assert.assertTrue(systemOutRule.getLog().contains("CWL ID"));
        Assert.assertTrue(systemOutRule.getLog().contains("md5sum"));
        Assert.assertTrue(systemOutRule.getLog().contains("File Size"));
        Assert.assertTrue(!new File("tooltester/FileReport.csv").exists());
    }

    @Test
    public void reportInvalidTool() {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa" };
        main(argv);
        assertReport();
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

    /**
     * Gets all the file combinations with any verified source.
     */
    @Test
    public void getVerifiedToolsTest() {
        int count;
        List<Tool> verifiedTools = client.getVerifiedTools();
        for (Tool verifiedTool : verifiedTools) {
            try {
                client.countNumberOfTests(verifiedTool);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files.  Got " + count, count != 0);
    }

    /**
     * Gets all the file combinations with specified sources.
     */
    @Test
    public void getVerifiedToolsWithFilterTest() {
        int count;
        List<String> verifiedSources = Collections.singletonList("Docktesters group");
        List<Tool> verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            try {
                client.countNumberOfTests(verifiedTool);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files. Got " + count, count != 0);
        client.setCount(0);
        verifiedSources = Arrays.asList("Docktesters group", "Another Group");
        verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            try {
                client.countNumberOfTests(verifiedTool);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files. Got " + count, count != 0);
        client.setCount(0);
        verifiedSources = Collections.singletonList("Another Group");
        verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            try {
                client.countNumberOfTests(verifiedTool);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files. Got " + count, count == 0);
    }

}
