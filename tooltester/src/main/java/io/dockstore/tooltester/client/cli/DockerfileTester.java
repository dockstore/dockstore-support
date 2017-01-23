package io.dockstore.tooltester.client.cli;

import java.io.IOException;
import java.util.Map;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.JobWithDetails;

/**
 * @author gluu
 * @since 20/01/17
 */
class DockerfileTester {
    private JenkinsServer jenkins;
    private String prefix = "DockerfileTest";

    DockerfileTester(JenkinsServer jenkins) {
        this.jenkins = jenkins;
    }

    /**
     * Creates a pipeline on Jenkins to test the dockerfile
     *
     * @param suffix  The suffix of the test name
     */

    void createTest(String suffix) {
        String name = prefix + suffix;
        try {
            String jobxml = jenkins.getJobXml(prefix);
            JobWithDetails job;
            job = jenkins.getJob(name);
            if (job == null) {
                jenkins.createJob(name, jobxml, true);
            } else {
                jenkins.updateJob(name, jobxml, true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the already-made DockerfileTest pipeline on Jenkins
     *
     * @param suffix    The suffix of the test name
     * @param parameter The Jenkins build parameters
     */
    void runTest(String suffix, Map<String, String> parameter) {
        String name = prefix + suffix;
        JobWithDetails job;
        try {
            job = jenkins.getJob(name);
            job.build(parameter, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a single tool's test results
     * @param suffix    The suffix of the test name
     * @return          Status of test
     */
    String getTestResults(String suffix) {
        String status = null;
        JobWithDetails job;
        String name;
        name = prefix + suffix;
        try {
            job = jenkins.getJob(name);
            Build build = job.getLastBuild();
            BuildWithDetails details = build.details();
            BuildResult result = details.getResult();
            if (details.isBuilding()) {
                status = "In-progress";
            } else {
                status = result.toString();
                if (result != BuildResult.SUCCESS) {
                    System.out.println(details.getConsoleOutputText());
                }
            }
            System.out.println(details.getResult());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return status;
    }
}
