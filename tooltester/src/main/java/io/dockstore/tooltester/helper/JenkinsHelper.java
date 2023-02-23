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
import io.dockstore.tooltester.blueOceanJsonObjects.PipelineImpl;
import io.dockstore.tooltester.blueOceanJsonObjects.PipelineNodeImpl;
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
    private String crumb;

    JenkinsHelper(HierarchicalINIConfiguration config) {
        this.config = config;
        setupJenkins();
    }

    public static String cleanSuffx(String name) {
        name = name.replaceAll("[/:#]", "-");
        return name;
    }

    public JenkinsServer getJenkins() {
        return jenkins;
    }

    public abstract String getPREFIX();

    public PipelineNodeImpl[] getBlueOceanJenkinsPipeline(String name) {
        String entity = getEntity("blue/rest/organizations/jenkins/pipelines/" + name);
        Gson gson = new Gson();
        PipelineImpl example = gson.fromJson(entity, PipelineImpl.class);
        String uri = example.getLatestRun().getLinks().getSelf().getHref();
        entity = getEntity(uri);
        PipelineNodeImpl link = gson.fromJson(entity, PipelineNodeImpl.class);
        uri = link.getLinks().getNodes().getHref();
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
            setCrumb(getJenkinsCrumb());
        } catch (URISyntaxException e) {
            exceptionMessage(e, "Jenkins server URI is not valid", CLIENT_ERROR);
        } catch (IOException e) {
            exceptionMessage(e, "Could not connect to Jenkins server", IO_ERROR);
        }
    }

    public String getEntity(String uri) {
        String entity = null;
        try {
            String crumb = getCrumb();

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
     * Constructs the name of the Pipeline on Jenkins based on several properties
     *
     * @param runner        The runner (cwltool, toil, cromwell)
     * @param ToolVersionId The ToolVersion ID, which is also equivalent to the Tool ID + version name
     * @return
     */
    public static String buildName(String runner, String ToolVersionId) {
        String prefix = PipelineTester.PREFIX;
        String name = String.join("-", prefix, runner, ToolVersionId);
        name = JenkinsHelper.cleanSuffx(name);
        return name;
    }

    public String getJenkinsJobTemplate() {
        String prefix = PipelineTester.PREFIX;
        try {
            return jenkins.getJobXml(prefix);
        } catch (IOException e) {
            errorMessage("Could not get Jenkins template job: " + prefix, COMMAND_ERROR);
        }
        return null;
    }

    /**
     * Creates a pipeline on Jenkins to test the parameter file
     *
     * @param name Name of the pipeline
     */
    public void createTest(String name, String jobTemplate) {
        JobWithDetails job = null;
        try {
            job = jenkins.getJob(name);
        } catch (IOException e) {
            exceptionMessage(e, "Could not get Jenkins job: " + name, IO_ERROR);
        }
        try {
            if (job == null) {
                jenkins.createJob(name, jobTemplate, true);
                LOG.info("Created Jenkins job: " + name);
            } else {
                jenkins.updateJob(name, jobTemplate, true);
                LOG.info("Updated Jenkins job: " + name);
            }
        } catch (IOException e) {
            exceptionMessage(e, "Could not create or update Jenkins job: " + name, IO_ERROR);
        }

    }

    /**
     * Run the already-made ParameterFileTest pipeline on Jenkins
     *
     * @param name      Name of the pipeline
     * @param parameter Input parameter for the test
     */
    public void runTest(String name, Map<String, String> parameter) {
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
     * @param name The suffix of the test name
     */
    public Map<String, String> getTestResults(String name) {

        String status;
        JobWithDetails job;
        Map<String, String> map = new HashMap<>();

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

    public boolean isRunning(String name) {
        try {
            JobWithDetails job = jenkins.getJob(name);
            Build build = job.getLastBuild();
            return build.details().isBuilding();
        } catch (Exception e) {
            LOG.info("Could not get Jenkins job: " + name);
            return false;
        }
    }

    public List<Build> getAllBuilds(String name) {
        List<Build> builds = null;
        try {
            builds = jenkins.getJob(name).getAllBuilds();
        } catch (IOException | NullPointerException e) {
            LOG.warn("Could not find job: " + name);
        }
        return builds;
    }

    public JobWithDetails getJenkinsJob(String name) {
        JobWithDetails job = null;
        try {
            job = jenkins.getJob(name);

        } catch (IOException e) {
            exceptionMessage(e, "Could not get Jenkins job", IO_ERROR);
        }
        return job;
    }

    public int getLastBuildId(String name) {
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

    private String getCrumb() {
        return crumb;
    }

    private void setCrumb(String crumb) {
        this.crumb = crumb;
    }
}
