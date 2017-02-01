/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.tooltester.client.cli;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.helper.JenkinsVersion;
import com.offbytwo.jenkins.model.Job;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.GAGHApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import static io.dockstore.tooltester.client.cli.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.CLIENT_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.DEBUG;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.exceptionMessage;

/**
 * Prototype for testing service
 */
public class Client {
    boolean development = false;
    private ContainersApi containersApi;
    private UsersApi usersApi;
    private GAGHApi ga4ghApi;
    private boolean isAdmin = false;
    private Report report;
    private int count = 0;
    private HierarchicalINIConfiguration config;
    private DockerfileTester dockerfileTester;
    private ParameterFileTester parameterFileTester;
    private JenkinsServer jenkins;

    public Client() {

    }

    /**
     * Main method
     *
     * @param argv Command line arguments
     */
    public static void main(String[] argv) {
        Client client = new Client();
        CommandMain cm = new CommandMain();
        JCommander jc = new JCommander(cm);
        CommandReport commandReport = new CommandReport();
        jc.addCommand("report", commandReport);
        jc.parse(argv);
        if (jc.getParsedCommand() != null) {
            switch (jc.getParsedCommand()) {
            case "report":
                if (commandReport.help) {
                    jc.usage("report");
                } else {
                    client.handleReport(commandReport.tools);
                }
                break;
            default:
                client.createToolTests(cm.api, cm.source, cm.execution);
            }
        } else {
            if (cm.help) {
                jc.usage();
            } else {
                client.createToolTests(cm.api, cm.source, cm.execution);
            }
        }

    }

    /**
     * This function handles the report command
     *
     * @param toolNames The tools passed in as arguments from the commmand line
     */
    private void handleReport(List<String> toolNames) {
        setupClientEnvironment();
        setupJenkins();
        setupTesters();
        List<Tool> tools = getVerifiedTools();
        openResults();
        if (!toolNames.isEmpty()) {
            tools = tools.parallelStream().filter(t -> toolNames.contains(t.getId())).collect(Collectors.toList());
        }
        for (Tool tool : tools) {
            getToolTestResults(tool);
        }
        finalizeResults();
    }

    /**
     * Deletes all the DockerfileTest and ParameterFileTest jobs and creates all the tests again
     * @param api   api to pull the tools from
     * @param source    the testing group that verified the tools
     * @param execution the location to test the tools
     */
    private void createToolTests(String api, List<String> source, String execution) {
        setupClientEnvironment();
        setupJenkins();
        setupTesters();
        deleteJobs("DockerfileTest");
        deleteJobs("ParameterFileTest");
        List<Tool> tools = getVerifiedTools(source);
        for (Tool tool : tools) {
            createToolTests(tool);
        }
    }

    public DockerfileTester getDockerfileTester() {
        return dockerfileTester;
    }

    public ParameterFileTester getParameterFileTester() {
        return parameterFileTester;
    }

    public JenkinsServer getJenkins() {
        return jenkins;
    }

    private void setJenkins(JenkinsServer jenkins) {
        this.jenkins = jenkins;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    protected void setupTesters() {
        dockerfileTester = new DockerfileTester(getJenkins());
        parameterFileTester = new ParameterFileTester(getJenkins());
    }

    protected void setupJenkins() {
        if (development) {
            try {
                String serverUrl;
                String username;
                String password;
                serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
                username = config.getString("jenkins-username", "jenkins");
                password = config.getString("jenkins-password", "jenkins");
                ExceptionHandler.LOG.info("The current configuration: " + serverUrl + " " + username + "" + password);
                setJenkins(new JenkinsServer(new URI(serverUrl), username, password));
                Map<String, Job> jobs = jenkins.getJobs();
                JenkinsVersion version = jenkins.getVersion();
                System.out.println("Jenkins is version " + version.getLiteralVersion() + " and has " + jobs.size() + " jobs");
            } catch (URISyntaxException e) {
                exceptionMessage(e, "Jenkins server URI is not valid", CLIENT_ERROR);
            } catch (IOException e) {
                exceptionMessage(e, "Could not connect to Jenkins server", IO_ERROR);
            }
        }
    }

    void setupClientEnvironment() {
        String userHome = System.getProperty("user.home");
        System.out.println(userHome);
        try {
            File configFile = new File(userHome + File.separator + ".tooltester" + File.separator + "config");
            this.config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            exceptionMessage(e, "", API_ERROR);
        }

        // pull out the variables from the config
        String token = config.getString("token", "");
        String serverUrl = config.getString("server-url", "https://www.dockstore.org:8443");
        this.development = config.getBoolean("development", true);
        ExceptionHandler.LOG.info("The current configuration: " + development + " " + serverUrl);

        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();
        defaultApiClient.addDefaultHeader("Authorization", "Bearer " + token);
        defaultApiClient.setBasePath(serverUrl);

        this.containersApi = new ContainersApi(defaultApiClient);
        this.usersApi = new UsersApi(defaultApiClient);
        this.ga4ghApi = new GAGHApi(defaultApiClient);

        try {
            if (this.usersApi.getApiClient() != null) {
                this.isAdmin = this.usersApi.getUser().getIsAdmin();
            }
        } catch (ApiException e) {
            //exceptionMessage(e, "Could not use user API", API_ERROR);
            System.out.println("Could not use user API.  Admin is false by default");
            this.isAdmin = false;
        }
        defaultApiClient.setDebugging(DEBUG.get());
    }

    /**
     * Finalize the results of testing
     */
    public void finalizeResults() {
        report.close();
    }

    public void openResults() {
        report = new Report("Report.csv");
    }

    /**
     * do the actual work here
     */
    private void run() {
        setupClientEnvironment();
        setupJenkins();
        boolean toolTestResult = false;
        /** use swagger-generated classes to talk to dockstore */
        try {
            final List<Tool> tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);
            System.out.println("Number of tools on Dockstore: " + tools.size());
            LongStream longStream = tools.parallelStream().filter(Tool::getVerified)
                    .mapToLong(tool -> tool.getVersions().parallelStream().filter(ToolVersion::getVerified).count());
            System.out.println("Number of versions of tools to test on Dockstore (currently): " + longStream.sum());
            //            toolTestResult = tools.parallelStream().filter(Tool::getVerified).map(this::testTool).reduce(true, Boolean::logicalAnd);
            //            System.out.println("Successful \"testing\" of tools found on Dockstore: " + toolTestResult);
        } catch (ApiException e) {
            exceptionMessage(e, "", API_ERROR);
        }
    }

    /**
     * This function counts the number of tests that need to be created
     *
     * @param verifiedTool The verified tool
     */
    public void countNumberOfTests(Tool verifiedTool) throws ApiException {
        SourceFile dockerfile;
        SourceFile descriptor;
        SourceFile testParameter;
        List<SourceFile> testParameterFiles;
        DockstoreTool dockstoreTool;
        Long containerId;
        dockstoreTool = containersApi.getPublishedContainerByToolPath(verifiedTool.getId());
        containerId = dockstoreTool.getId();
        for (ToolVersion version : verifiedTool.getVersions()) {
            String tag = version.getName();
            dockerfile = containersApi.dockerfile(containerId, tag);
            for (ToolVersion.DescriptorTypeEnum descriptorType : version.getDescriptorType()) {
                switch (descriptorType.toString()) {
                case "CWL":
                    descriptor = containersApi.cwl(containerId, tag);
                    break;
                case "WDL":
                    descriptor = containersApi.wdl(containerId, tag);
                    break;
                default:
                    break;
                }
                testParameterFiles = containersApi.getTestParameterFiles(containerId, tag, descriptorType.toString());
                for (SourceFile testParameterFile : testParameterFiles) {
                    testParameter = testParameterFile;
                    count++;
                }
            }
        }
    }

    /**
     * Creates the pipeline(s) on Jenkins to test a tool
     *
     * @param tool The tool to create tests for
     */
    void createToolTests(Tool tool) {
        DockstoreTool dockstoreTool = null;
        try {
            dockstoreTool = getContainersApi().getPublishedContainerByToolPath(tool.getId());
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get published containers using the container api", API_ERROR);
        }
        List<ToolVersion> toolVersions = tool.getVersions();
        for (ToolVersion toolversion : toolVersions) {
            assert dockstoreTool != null;
            Long containerId = dockstoreTool.getId();
            String id = toolversion.getId();
            String tag = toolversion.getName();
            String name = id;
            name = name.replace("/", "-");
            name = name.replace(":", "-");
            dockerfileTester.createTest(name);

            try {
                List<SourceFile> testParameterFiles;
                for (ToolVersion.DescriptorTypeEnum descriptorType : toolversion.getDescriptorType()) {
                    testParameterFiles = containersApi.getTestParameterFiles(containerId, tag, descriptorType.toString());
                    for (SourceFile testParameterFile : testParameterFiles) {
                        String path = testParameterFile.getPath();
                        path = path.replaceFirst("^/", "");
                        path = path.replace("/", "-");
                        parameterFileTester.createTest(name + "-" + path);
                    }
                }
            } catch (ApiException e) {
                exceptionMessage(e, "Could not get test parameter files using the container api", API_ERROR);
            }
        }
    }

    /**
     * Creates the pipeline(s) on Jenkins to test a tool
     *
     * @param tool The tool to get the test results for
     */
    void getToolTestResults(Tool tool) {
        DockstoreTool dockstoreTool = null;
        try {
            dockstoreTool = containersApi.getPublishedContainerByToolPath(tool.getId());
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get published containers using the container api", API_ERROR);
        }
        List<ToolVersion> toolVersions = tool.getVersions();
        for (ToolVersion toolversion : toolVersions) {
            Long containerId = dockstoreTool.getId();
            String id = toolversion.getId();
            String tag = toolversion.getName();
            String name = id;
            name = name.replace("/", "-");
            name = name.replace(":", "-");
            Date date = new Date();
            String dockerfileStatus;
            Map<String, String> map = dockerfileTester.getTestResults(name);
            dockerfileStatus = map.get("status");
            if (!dockerfileStatus.equals("SUCCESS") && !dockerfileStatus.equals("NOT_BUILT")) {

                dockerfileStatus = dockerfileStatus + " See " + dockerfileTester.getConsoleOutputFilePath(name);

            }
            try {
                List<SourceFile> testParameterFiles;
                for (ToolVersion.DescriptorTypeEnum descriptorType : toolversion.getDescriptorType()) {
                    testParameterFiles = containersApi.getTestParameterFiles(containerId, tag, descriptorType.toString());
                    for (SourceFile testParameterFile : testParameterFiles) {
                        count++;
                        String path = testParameterFile.getPath();
                        path = path.replaceFirst("^/", "");
                        path = path.replace("/", "-");
                        String status;
                        Map<String, String> map2 = parameterFileTester.getTestResults(name + "-" + path);
                        status = map2.get("status");
                        if (!status.equals("SUCCESS") && !status.equals("NOT_BUILT")) {
                            status = status + " See " + parameterFileTester.getConsoleOutputFilePath(name + "-" + path);
                        }
                        Long duration = Long.valueOf(map.get("duration"));
                        String durationString = Duration.ofMillis(duration).toString();
                        List<String> record = Arrays
                                .asList(toolversion.getId(), map.get("date"), tag, "Local", testParameterFile.getPath(), durationString,
                                        status, dockerfileStatus);
                        report.writeLine(record);
                    }
                }
            } catch (ApiException e) {
                exceptionMessage(e, "Could not get test parameter files using the container API", API_ERROR);
            }
        }
    }

    /**
     * This function deletes all jobs on jenkins matching "Test.*"
     */
    void deleteJobs(String pattern) {
        try {
            Map<String, Job> jobs = jenkins.getJobs();
            jobs.entrySet().stream().filter(map -> map.getKey().matches(pattern + ".+")).forEach(map -> {
                try {
                    jenkins.deleteJob(map.getKey(), true);
                } catch (IOException e) {
                    exceptionMessage(e, "Could not delete Jenkins job", IO_ERROR);
                }
            });
        } catch (IOException e) {
            exceptionMessage(e, "Could not find and delete Jenkins job", IO_ERROR);
        }
    }

    /**
     * This function checks if the any of the tool's verified source matches the filter
     *
     * @param filter          The list of verified sources that we're interested in
     * @param verifiedSources The tool version's verified sources
     * @return True if the one of the verified sources matches the filter
     */
    private boolean matchVerifiedSource(List<String> filter, String verifiedSources) {
        return filter.stream().anyMatch(str -> str.trim().equals(verifiedSources));

    }

    /**
     * Gets the list of verified tools
     *
     * @return The list of verified tools
     */
    List<Tool> getVerifiedTools() {
        List<Tool> verifiedTools = null;
        try {
            final List<Tool> tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);
            verifiedTools = tools.parallelStream().filter(Tool::getVerified).collect(Collectors.toList());
            for (Tool tool : verifiedTools) {
                tool.setVersions(tool.getVersions().parallelStream().filter(ToolVersion::getVerified).collect(Collectors.toList()));
            }
        } catch (ApiException e) {
            exceptionMessage(e, "", API_ERROR);
        }
        return verifiedTools;
    }

    /**
     * Gets the list of verified tools and applies filter to it
     *
     * @param verifiedSources Filter parameter to filter the verified sources
     * @return The list of verified tools
     */
    List<Tool> getVerifiedTools(List<String> verifiedSources) {
        List<Tool> verifiedTools = getVerifiedTools();
        for (Tool tool : verifiedTools) {
            tool.setVersions(tool.getVersions().parallelStream().filter(p -> matchVerifiedSource(verifiedSources, p.getVerifiedSource()))
                    .collect(Collectors.toList()));
        }
        return verifiedTools;
    }

    /**
     * test the tool by
     * 1) Running available parameter files
     * 2) Rebuilding docker images
     * 3) Storing results for each tool as it finishes
     * <p>
     * in the early phases of the project, we can try to run these locally sequentially
     * however, due to system requirements, it will quickly become necessary to hook this up to
     * either a fixed network of slaves or Consonance on-demand hosts
     *
     * @param tool The tool to test
     * @return boolean  Returns true
     */
    boolean testTool(Tool tool) {
        DockstoreTool dockstoreTool = null;
        try {
            dockstoreTool = containersApi.getPublishedContainerByToolPath(tool.getId());
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get published containers using the container API", API_ERROR);
        }
        Map<String, String> parameter = new HashMap<>();
        List<ToolVersion> toolVersions = tool.getVersions();
        for (ToolVersion toolversion : toolVersions) {
            String url = dockstoreTool.getGitUrl();
            url = url.replace("git@github.com:", "https://github.com/");
            String dockerfilePath = null;
            Long containerId = dockstoreTool.getId();
            String id = toolversion.getId();
            String tag = toolversion.getName();
            try {
                SourceFile dockerfile = containersApi.dockerfile(containerId, tag);
                dockerfilePath = dockerfile.getPath().replaceFirst("^/", "");
            } catch (ApiException e) {
                exceptionMessage(e, "Could not get dockerfile using the container API", API_ERROR);
            }

            String name = id;
            name = name.replace("/", "-");
            name = name.replace(":", "-");
            parameter.put("Tag", tag);
            parameter.put("URL", url);
            parameter.put("DockerfilePath", dockerfilePath);

            dockerfileTester.runTest(name, parameter);

            try {
                List<SourceFile> testParameterFiles;
                SourceFile descriptor;
                for (ToolVersion.DescriptorTypeEnum descriptorType : toolversion.getDescriptorType()) {
                    switch (descriptorType.toString()) {
                    case "CWL":
                        descriptor = containersApi.cwl(containerId, tag);
                        break;
                    case "WDL":
                        descriptor = containersApi.wdl(containerId, tag);
                        break;
                    default:
                        descriptor = null;
                        break;
                    }
                    testParameterFiles = containersApi.getTestParameterFiles(containerId, tag, descriptorType.toString());
                    for (SourceFile testParameterFile : testParameterFiles) {
                        String path = testParameterFile.getPath();
                        path = path.replaceFirst("^/", "");
                        parameter.put("ParameterPath", path);
                        path = path.replace("/", "-");
                        parameter.put("DescriptorPath", descriptor.getPath().replaceFirst("^/", ""));
                        parameterFileTester.runTest(name + "-" + path, parameter);
                    }
                }
            } catch (ApiException e) {
                exceptionMessage(e, "Could not get cwl or wdl and test parameter files using the container API", API_ERROR);
            }
        }
        return true;
    }

    public ContainersApi getContainersApi() {
        return containersApi;
    }

    public GAGHApi getGa4ghApi() {
        return ga4ghApi;
    }

    public void setGa4ghApi(GAGHApi ga4ghApi) {
        this.ga4ghApi = ga4ghApi;
    }

    @Parameters(separators = "=", commandDescription = "Report status of tools tested")
    private static class CommandReport {
        @Parameter(names = "--all", description = "Whether to report all tools or not")
        private Boolean all = false;
        @Parameter(names = "--tool", description = "The specific tools to report", variableArity = true)
        private List<String> tools = new ArrayList<>();
        @Parameter(names = "--help", help = true)
        private boolean help;
    }

    private static class CommandMain {
        @Parameter(names = { "--execution", "--runtime-environment" }, description = "Location of Testing")
        private String execution = "local";
        @Parameter(names = { "--source" }, description = "Tester Group")
        private List<String> source = new ArrayList<>();
        @Parameter(names = { "--api", "--dockstore-url" }, description = "dockstore install that we wish to test tools from")
        private String api = "";
        @Parameter(names = "--help", description = "Prints help for main", help = true)
        private boolean help = false;
    }
}

