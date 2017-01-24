package io.dockstore.tooltester.client.cli;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.JobWithDetails;

/**
 * @author gluu
 * @since 24/01/17
 */
public abstract class JenkinsJob {
    public abstract String getPREFIX();

    private JenkinsServer jenkins;

    JenkinsJob(JenkinsServer jenkins) {
        this.jenkins = jenkins;
    }

    /**
     * Creates a pipeline on Jenkins to test the parameter file
     *
     * @param suffix The suffix of the test name
     */

    void createTest(String suffix) {

        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
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
     * Run the already-made ParameterFileTest pipeline on Jenkins
     *
     * @param suffix    Suffix of the test name
     * @param parameter Input parameter for the test
     */
    void runTest(String suffix, Map<String, String> parameter) {
        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
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
     *
     * @param suffix The suffix of the test name
     */
    String getTestResults(String suffix) {
        String prefix = getPREFIX();
        String status = null;
        JobWithDetails job;
        String name;
        name = prefix + "-" + suffix;
        try {
            job = jenkins.getJob(name);
            Build build = job.getLastBuild();
            BuildWithDetails details = build.details();
            BuildResult result = details.getResult();
            details.getDuration();
            if (details.isBuilding()) {
                status = "In-progress";
            } else {
                status = result.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return status;
    }

    /**
     * Creates the consoleOutputFile
     *
     * @param suffix The suffix part of the Jenkins job name
     * @return The path to the output file
     */
    String createConsoleOutputFile(String suffix) {
        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
        Path path = null;
        JobWithDetails job;
        try {
            job = jenkins.getJob(name);
            Build build = job.getLastBuild();
            BuildWithDetails details = build.details();
            String consoleOutputText = details.getConsoleOutputText();
            path = Paths.get("./target/" + name + ".txt");
            Files.write(path, consoleOutputText.getBytes(Charset.forName("UTF-8")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return path != null ? path.toString() : null;
    }
}
