package io.dockstore.tooltester.helper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import com.google.gson.Gson;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.helper.JenkinsVersion;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import io.dockstore.tooltester.blueOcean.PipelineImpl;
import io.dockstore.tooltester.blueOcean.PipelineNodeImpl;
import io.dockstore.tooltester.jenkins.CrumbJsonResult;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.tooltester.helper.ExceptionHandler.CLIENT_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.errorMessage;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;

//import com.offbytwo.jenkins.model.Artifact;

/**
 * @author gluu
 * @since 24/01/17
 */
public abstract class JenkinsHelper {
    private static final Logger LOG = LoggerFactory.getLogger(JenkinsHelper.class);
    private JenkinsServer jenkins;
    private HierarchicalINIConfiguration config;
    JenkinsHelper(HierarchicalINIConfiguration config) {
        this.config = config;
        setupJenkins();
    }

    public JenkinsServer getJenkins() {
        return jenkins;
    }

    public abstract String getPREFIX();

    public PipelineNodeImpl[] getBlueOceanJenkinsPipeline(String suffix) {
        String entity = getEntity("blue/rest/organizations/jenkins/pipelines/PipelineTest-" + suffix);
        Gson gson = new Gson();
        PipelineImpl example = gson.fromJson(entity, PipelineImpl.class);
        String uri = example.getLatestRun().getLinks().getNodes().getHref();
        entity = getEntity(uri);
        return gson.fromJson(entity, PipelineNodeImpl[].class);
    }

    private void setupJenkins() {
        try {
            String serverUrl;
            String username;
            String password;
            serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
            username = config.getString("jenkins-username", "travis");
            password = config.getString("jenkins-password", "travis");
            this.jenkins = new JenkinsServer(new URI(serverUrl), username, password);
            Map<String, Job> jobs = jenkins.getJobs();
            JenkinsVersion version = jenkins.getVersion();
            LOG.trace("Jenkins is version " + version.getLiteralVersion() + " and has " + jobs.size() + " jobs");
        } catch (URISyntaxException e) {
            exceptionMessage(e, "Jenkins server URI is not valid", CLIENT_ERROR);
        } catch (IOException e) {
            exceptionMessage(e, "Could not connect to Jenkins server", IO_ERROR);
        }
    }

    /**
     * This function deletes all jobs on jenkins matching "Test.*"
     * Only used by admin
     */
    //    public void deleteJobs(String pattern) {
    //        try {
    //            Map<String, Job> jobs = jenkins.getJobs();
    //            jobs.entrySet().stream().filter(map -> map.getKey().matches(pattern + ".+")).forEach(map -> {
    //                try {
    //
    //                    jenkins.deleteJob(map.getKey(), true);
    //                } catch (IOException e) {
    //                    exceptionMessage(e, "Could not delete Jenkins job", IO_ERROR);
    //                }
    //            });
    //        } catch (IOException e) {
    //            exceptionMessage(e, "Could not find and delete Jenkins job", IO_ERROR);
    //        }
    //    }
    public String getEntity(String uri) {
        String entity = null;
        try {
            String crumb = getJenkinsCrumb();

            // The configuration file is only used for production.  Otherwise it defaults to the travis ones.
            String username = config.getString("jenkins-username", "travis");
            String password = config.getString("jenkins-password", "travis");
            String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
            HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);
            javax.ws.rs.client.Client client = ClientBuilder.newClient().register(feature);
            entity = client.target(serverUrl).path(uri).request(MediaType.TEXT_PLAIN_TYPE).header("crumbRequestField", crumb)
                    .get(String.class);
        } catch (Exception e) {
            LOG.warn("Could not get Jenkins stage: " + uri);
        }
        return entity;
    }

    /**
     * This function gets the jenkins crumb in the event that the java jenkins api does not work
     *
     * @return The crumb string
     */
    private String getJenkinsCrumb() {
        String username = config.getString("jenkins-username", "travis");
        String password = config.getString("jenkins-password", "travis");
        String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
        HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);

        javax.ws.rs.client.Client client = ClientBuilder.newClient();
        client.register(feature);
        String entity = client.target(serverUrl).path("crumbIssuer/api/json").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
        Gson gson = new Gson();
        CrumbJsonResult result = gson.fromJson(entity, CrumbJsonResult.class);
        return result.crumb;
    }

    /**
     * Creates a pipeline on Jenkins to test the parameter file
     *
     * @param suffix The suffix of the test name
     */

    public void createTest(String suffix) {

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
    public void runTest(String suffix, Map<String, String> parameter) {
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
    public Map<String, String> getTestResults(String suffix) {
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

    public boolean isRunning(String suffix) {
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

    public List<Build> getAllBuilds(String suffix) {
        String prefix = getPREFIX();
        String name = prefix + "-" + suffix;
        List<Build> builds = null;
        try {
            builds = jenkins.getJob(name).getAllBuilds();
        } catch (IOException | NullPointerException e) {
            LOG.warn("Could not find job: " + name);
        }
        return builds;
    }

    public JobWithDetails getJenkinsJob(String suffix) {
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

    public int getLastBuildId(String suffix) {
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
