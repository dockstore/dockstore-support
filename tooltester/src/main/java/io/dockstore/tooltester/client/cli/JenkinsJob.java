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

import static io.dockstore.tooltester.client.cli.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.exceptionMessage;

/**
 * @author gluu
 * @since 24/01/17
 */
public abstract class JenkinsJob {
    private JenkinsServer jenkins;

    JenkinsJob(JenkinsServer jenkins) {
        this.jenkins = jenkins;
    }

    public abstract String getPREFIX();

    /**
     * Creates a pipeline on Jenkins to test the parameter file
     *
     * @param suffix The suffix of the test name
     */

    void createTest(String suffix) {

        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
        JobWithDetails job = null;
        String jobxml = null;
        try {
            jobxml = jenkins.getJobXml(prefix);
            job = jenkins.getJob(name);
        } catch (IOException e) {
            exceptionMessage(e, "Could not get Jenkins job", IO_ERROR);
        }

        if (job == null) {
            try {
                jenkins.createJob(name, jobxml, true);
            } catch (IOException e) {
                exceptionMessage(e, "Could not create Jenkins job", IO_ERROR);
            }
        } else {
            try {
                jenkins.updateJob(name, jobxml, true);
            } catch (IOException e) {
                exceptionMessage(e, "Could not update existing Jenkins job", IO_ERROR);
            }
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
            exceptionMessage(e, "Cannot get Jenkins job", IO_ERROR);
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
            exceptionMessage(e, "Could not get Jenkins job results", IO_ERROR);
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
            exceptionMessage(e, "Could not get Jenkins job", IO_ERROR);
        }

        return path != null ? path.toString() : null;
    }
}
