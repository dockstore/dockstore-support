package io.dockstore.tooltester.client.cli;

import java.util.HashMap;
import java.util.Map;

import io.dockstore.tooltester.helper.PipelineTester;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * @author gluu
 * @since 24/01/17
 */

/**
 * Many tests ignored due to reasons explained in this PR https://github.com/dockstore/dockstore-support/pull/448
 */
@ExtendWith(SystemStubsExtension.class)
public class JenkinsJobTest {

    /**
     * This tests if a parameter file test can be created and ran.
     * Also tests if the test results can be attained and the console output file can be generated
     */
    @SystemStub
    private SystemErr systemErr = new SystemErr(new NoopStream());
    private Client client;

    @BeforeEach
    public void initialize() {
        client = new Client();
        client.setupClientEnvironment();
        assertNotNull(client.getContainersApi(), "client API could not start");
    }

    @Disabled
    @Test
    public void pipelineTestJobIT() {
        final String suffix = "id-tag";
        client.setupTesters();
        assertNotNull(client.getPipelineTester().getJenkins(), "Jenkins server can not be reached");
        client.setupTesters();
        PipelineTester pipelineTester = client.getPipelineTester();
        String jenkinsJobTemplate = pipelineTester.getJenkinsJobTemplate();
        pipelineTester.createTest(suffix, jenkinsJobTemplate);

        Map<String, String> map = new HashMap<>();
        map.put("URL", "https://github.com/CancerCollaboratory/dockstore-tool-kallisto.git");
        map.put("Tag", "master");
        map.put("DockerfilePath", "Dockerfile");
        map.put("DescriptorPath", "Dockstore.cwl Dockstore.cwl");
        map.put("ParameterPath", "test1.json test2.json");

        pipelineTester.runTest(suffix, map);
        String status = pipelineTester.getTestResults(suffix).get("status");
        assertNotNull(status, "Status is not SUCCESS: ");
        //        try {
        //            client.getJenkins().deleteJob("PipelineTest" + "-" + suffix, true);
        //        } catch (IOException e) {
        //            System.out.println("Could not delete Jenkins job");
        //        }
    }

    @Disabled
    @Test
    public void getNonExistantTest() throws Exception {
        int exitCode = catchSystemExit(() -> {
            client.setupTesters();
        });
        assertEquals(10, exitCode);
        PipelineTester pipelineTester = client.getPipelineTester();
        assertNotNull(pipelineTester.getJenkins(), "Jenkins server can not be reached");
        pipelineTester.getTestResults("SuffixOfATestThatShouldNotExist");
    }
}
