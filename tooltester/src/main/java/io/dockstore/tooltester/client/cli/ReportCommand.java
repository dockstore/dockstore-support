package io.dockstore.tooltester.client.cli;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import io.dockstore.tooltester.S3Client;
import io.dockstore.tooltester.TooltesterConfig;
import io.dockstore.tooltester.blacklist.BlackList;
import io.dockstore.tooltester.blueOceanJsonObjects.PipelineNodeImpl;
import io.dockstore.tooltester.blueOceanJsonObjects.PipelineStepImpl;
import io.dockstore.tooltester.helper.GA4GHHelper;
import io.dockstore.tooltester.helper.PipelineTester;
import io.dockstore.tooltester.helper.TimeHelper;
import io.dockstore.tooltester.helper.TinyUrl;
import io.dockstore.tooltester.report.StatusReport;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.tooltester.client.cli.Client.getApiClient;
import static io.dockstore.tooltester.helper.ExceptionHandler.CLIENT_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.errorMessage;
import static io.dockstore.tooltester.helper.JenkinsHelper.buildName;

/**
 * @author gluu
 * @since 06/05/19
 */
final class ReportCommand {
    private static final Logger LOG = LoggerFactory.getLogger(ReportCommand.class);
    private static final boolean SEND_LOGS = true;
    private PipelineTester pipelineTester;
    private TooltesterConfig tooltesterConfig;
    private Ga4GhApi ga4ghApi;
    private String jenkinsServerUrl;

    /**
     * Setup the Report command
     */
    ReportCommand() {
        tooltesterConfig = new TooltesterConfig();
        ga4ghApi = new Ga4GhApi(getApiClient(tooltesterConfig.getServerUrl()));
        pipelineTester = new PipelineTester(tooltesterConfig.getConfig());
        jenkinsServerUrl = tooltesterConfig.getJenkinsServerUrl();
    }

    /**
     * Blue Ocean REST API does not know how long the job is running for
     * If it's still running, we get the duration since the start date
     * If it's finished running, we sum up the duration of each step
     *
     * @param state   The state of the Jenkins step ("RUNNING", "SUCCESS", etc)
     * @param date    When the Jenkins Job started
     * @param runtime The combined time of each step of the Jenkins Job in milliseconds
     * @return The formatted duration of the Job ("6h 5m")
     */
    private static String getDuration(String state, String date, Long runtime) {
        if (state.equals(StateEnum.RUNNING.name())) {
            return TimeHelper.getDurationSinceDate(date);
        } else {
            return TimeHelper.durationToString(runtime);
        }
    }

    /**
     * The main function to report on previous ran tools
     * @param toolNames List of specific tools to report on, otherwise reports on all of the verified ones by default
     * @param sources   List of verified sources to report on, otherwise reports on all of them by default
     */
    void report(List<String> toolNames, List<String> sources) {
        List<Tool> tools = GA4GHHelper.getTools(ga4ghApi, true, sources, toolNames);
        String prefix = TimeHelper.getDateFilePrefix();
        StatusReport report = new StatusReport(prefix + "Report.csv");
        tools.forEach(tool -> getToolTestResults(tool, report));
        report.close();
    }

    /**
     * Creates the pipeline(s) on Jenkins to test a tool
     *
     * @param tool The tool to get the test results for
     */
    private void getToolTestResults(Tool tool, StatusReport report) {
        String toolId = tool.getId();
        String[] runners = tooltesterConfig.getRunner();
        tool.getVersions().stream().filter(toolVersion -> BlackList.isNotBlacklisted(toolId, toolVersion.getName()))
                        .forEach(toolVersion -> Arrays.stream(runners).forEach(
                                runner -> getToolVersionTestResults(toolVersion, runner, report, toolId)));
    }

    /**
     * Get the test results for a specific tool version and runner
     *
     * @param toolVersion      The tool version to get the results
     * @param runner           The runner ("cwltool", "cromwell", etc)
     * @param report           The StatusReport object
     * @param toolId           The ID of the TRS Tool
     */
    private void getToolVersionTestResults(ToolVersion toolVersion, String runner, StatusReport report,
            String toolId) {
        if (toolVersion != null) {
            String toolVersionId = toolVersion.getId();
            String tag = toolVersion.getName();
            String jenkinsJobName = buildName(runner, toolVersionId);
            if (pipelineTester.getJenkinsJob(jenkinsJobName) == null) {
                LOG.info("Could not get job: " + jenkinsJobName);
                return;
            }
            int buildId = pipelineTester.getLastBuildId(jenkinsJobName);
            if (buildId == 0 || buildId == -1) {
                LOG.info("No build was ran for " + jenkinsJobName);
                return;
            }
            PipelineNodeImpl[] pipelineNodes = pipelineTester.getBlueOceanJenkinsPipeline(jenkinsJobName);
            // There's pretty much always going to be a "Parallel" node that does not matter
            List<PipelineNodeImpl> filteredNodes = Arrays.stream(pipelineNodes)
                    .filter(pipelineNode -> !pipelineNode.getDisplayName().equals("Parallel") && pipelineNode.getDurationInMillis() > 0L)
                    .collect(Collectors.toList());

            filteredNodes.forEach(pipelineNode -> {
                try {
                    String state = pipelineNode.getState();
                    String result = pipelineNode.getResult();
                    if (state.equals(StateEnum.RUNNING.name())) {
                        result = state;
                    }
                    String entity = pipelineTester.getEntity(pipelineNode.getLinks().getSteps().getHref());
                    String nodeLogURI = pipelineNode.getLinks().getSelf().getHref() + "log";
                    String longURL = jenkinsServerUrl + nodeLogURI;
                    String logURL = TinyUrl.getTinyUrl(longURL);
                    String logContent = pipelineTester.getEntity(nodeLogURI);
                    Gson gson = new Gson();
                    PipelineStepImpl[] pipelineSteps = gson.fromJson(entity, PipelineStepImpl[].class);
                    Long runtime = Arrays.stream(pipelineSteps).map(PipelineStepImpl::getDurationInMillis).mapToLong(Long::longValue).sum();
                    String date = pipelineNode.getStartTime();
                    String epochStartTime = TimeHelper.timeFormatToEpoch(date);
                    String duration = getDuration(state, date, runtime);
                    try {
                        date = TimeHelper.timeFormatConvert(date);
                    } catch (ParseException e) {
                        errorMessage("Could not parse start time " + date, CLIENT_ERROR);
                    }
                    List<String> record = Arrays
                            .asList(date, toolVersion.getId(), tag, runner, pipelineNode.getDisplayName(), result, duration, logURL);
                    report.printAndWriteLine(record);
                    if (SEND_LOGS) {
                        if (result.equals(StateEnum.SUCCESS.name())) {
                            S3Client s3Client = new S3Client();
                            s3Client.createObject(toolId, tag, pipelineNode.getDisplayName(), runner, logContent, epochStartTime);
                        }
                    }
                } catch (NullPointerException e) {
                    LOG.error(e.getMessage(), e);
                }
            });
        } else {
            errorMessage("Tool version is null", COMMAND_ERROR);
        }
    }

    /**
     * State of the Jenkins Job
     */
    public enum StateEnum {
        SUCCESS, RUNNING, FAILURE
    }
}

