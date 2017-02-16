package io.dockstore.tooltester.client.cli;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.JobWithDetails;
import io.dockstore.tooltester.jenkins.OutputFile;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.tooltester.client.cli.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.errorMessage;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.exceptionMessage;

//import com.offbytwo.jenkins.model.Artifact;

/**
 * @author gluu
 * @since 24/01/17
 */
public abstract class JenkinsJob {
    private static final Logger LOG = LoggerFactory.getLogger(JenkinsJob.class);
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
            if (jobxml == null) {
                errorMessage("Could not get Jenkins template job", COMMAND_ERROR);
            }
            job = jenkins.getJob(name);
        } catch (IOException e) {
            exceptionMessage(e, "Could not get Jenkins job", IO_ERROR);
        }

        if (job == null) {
            try {
                jenkins.createJob(name, jobxml, true);
                LOG.info("Created Jenkins job: " + name);
            } catch (IOException e) {
                exceptionMessage(e, "Could not create Jenkins job", IO_ERROR);
            }
        } else {
            try {
                jenkins.updateJob(name, jobxml, true);
                LOG.info("Updated Jenkins job: " + name);
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
            if (job == null) {
                LOG.info("Could not get Jenkins job: " + name);
            } else {
                job.build(parameter, true);
                LOG.info("Running: " + name);
            }
        } catch (IOException e) {
            exceptionMessage(e, "Cannot get Jenkins job", IO_ERROR);
        }

    }

    /**
     * Retrieves a single tool's test results
     *
     * @param suffix The suffix of the test name
     */
    Map<String, String> getTestResults(String suffix) {
        String prefix = getPREFIX();
        String status;
        JobWithDetails job;
        String name;
        Map<String, String> map = new HashMap<>();
        name = prefix + "-" + suffix;

        try {
            job = jenkins.getJob(name);
            Build build = job.getLastBuild();

            BuildWithDetails details = build.details();
            BuildResult result = details.getResult();
            if (!details.isBuilding()) {
                status = result.toString();
                map.put("status", status);
            } else {
                map.put("status", "Building");
            }
            System.out.println(build.getUrl());
            LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(details.getTimestamp()), ZoneId.systemDefault());

            map.put("duration", String.valueOf(details.getDuration()));
            map.put("date", date.toString());

        } catch (IOException e) {
            exceptionMessage(e, "Could not get Jenkins job results", IO_ERROR);
        } catch (NullPointerException e) {
            errorMessage("Could not get Jenkins job", COMMAND_ERROR);
        }
        return map;
    }

    boolean isRunning(String suffix) {
        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
        try {
            JobWithDetails job = jenkins.getJob(name);
            Build build = job.getLastBuild();
            return build.details().isBuilding();
        } catch (Exception e) {
            LOG.info("Could not get Jenkins job: " + name);
            return false;
        }
    }

    Map<Integer, List<Map<String, OutputFile>>> getAllArtifactsAllBuilds(String suffix) {
        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
        List<Build> builds = null;
        Map<Integer, List<Map<String, OutputFile>>> map = new HashMap<>();
        try {
            builds = jenkins.getJob(name).getAllBuilds();
        } catch (IOException | NullPointerException e) {
            LOG.warn("Could not find job: " + name);
            return null;
        }
        for (Build build : builds) {
            int buildId = build.getNumber();
            List<Map<String, OutputFile>> artifactStrings = new ArrayList<>();
            try {
                List<Artifact> artifacts = build.details().getArtifacts();
                for (Artifact artifact : artifacts) {
                    try {
                        InputStream inputStream = build.details().downloadArtifact(artifact);
                        String artifactString = IOUtils.toString(inputStream, "UTF-8");
                        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
                        Type mapType = new TypeToken<Map<String, OutputFile>>() {
                        }.getType();
                        Map<String, OutputFile> map2 = gson.fromJson(artifactString, mapType);
                        artifactStrings.add(map2);
                    } catch (URISyntaxException e) {
                        exceptionMessage(e, "Could not download artifact", GENERIC_ERROR);
                    }
                }

            } catch (IOException e) {
                exceptionMessage(e, "Could not get artifacts", IO_ERROR);
            }
            map.put(buildId, artifactStrings);
        }
        return map;
    }

    JobWithDetails getJenkinsJob(String suffix) {
        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
        JobWithDetails job = null;
        try {
            job = jenkins.getJob(name);

        } catch (IOException e) {
            exceptionMessage(e, "Could not get Jenkins job", IO_ERROR);
        }
        return job;
    }

    int getLastBuildId(String suffix) {
        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
        int buildId = 0;
        try {
            JobWithDetails job = jenkins.getJob(name);
            Build build = job.getLastBuild();
            buildId = build.getNumber();
        } catch (IOException e) {
            exceptionMessage(e, "Could not get Jenkins job", IO_ERROR);
        } catch (NullPointerException e) {
            return -1;
        }
        return buildId;
    }
}
