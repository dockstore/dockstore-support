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
//import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
//import com.github.dockerjava.api.DockerClient;
//import com.github.dockerjava.api.command.SaveImageCmd;
//import com.github.dockerjava.api.exception.DockerException;
//import com.github.dockerjava.core.DefaultDockerClientConfig;
//import com.github.dockerjava.core.DockerClientBuilder;
//import com.github.dockerjava.core.DockerClientConfig;
//import com.github.dockerjava.core.command.PullImageResultCallback;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.helper.JenkinsVersion;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
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
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
//import org.apache.commons.io.FileUtils;
//import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prototype for testing service
 */
public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private final OptionSet options;
    private ContainersApi containersApi;
    private UsersApi usersApi;
    private GAGHApi ga4ghApi;
    private boolean isAdmin = false;
    private int count = 0;
    public static final int GENERIC_ERROR = 1; // General error, not yet described by an error type
    public static final int CONNECTION_ERROR = 150; // Connection exception
    public static final int IO_ERROR = 3; // IO throws an exception
    public static final int API_ERROR = 6; // API throws an exception
    public static final int CLIENT_ERROR = 4; // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10; // Command is not successful, but not due to errors

    private static final AtomicBoolean DEBUG = new AtomicBoolean(false);
    private HierarchicalINIConfiguration config;

    private static void errorMessage(String message, int exitCode) {
        err(message);
        System.exit(exitCode);
    }

    private static void err(String format, Object... args) {
        System.err.println(String.format(format, args));
    }

    public Client(OptionSet options) {
        this.options = options;
    }

    private static void exceptionMessage(Exception exception, String message, int exitCode) {
        if (!message.equals("")) {
            err(message);
        }
        if (Client.DEBUG.get()) {
            exception.printStackTrace();
        } else {
            err(exception.toString());
        }

        System.exit(exitCode);
    }

    /**
     * Main method
     *
     * @param argv  Command line argumetns
     */
    public static void main(String[] argv) {
        OptionParser parser = new OptionParser();
        parser.accepts("dockstore-url", "dockstore install that we wish to test tools from").withRequiredArg().defaultsTo("");
        parser.accepts("runtime-environment", "determines where to test tools").withRequiredArg().defaultsTo("local");

        final OptionSet options = parser.parse(argv);

        Client client = new Client(options);
        client.run();

        client.finalizeResults();

        boolean jenkinsExperiment = true;
        if (jenkinsExperiment) {
            JenkinsServer jenkins = null;
            try {
                jenkins = new JenkinsServer(new URI("http://142.1.177.103:8080"), "admin", "dummy password");
                Map<String, Job> jobs = jenkins.getJobs();
                JenkinsVersion version = jenkins.getVersion();
                JobWithDetails test = jobs.get("test").details();
                //jenkins.createJob("test2", "test");
                System.out.println("Jenkins is version " + version.getLiteralVersion() + " and has " + jobs.size() + " jobs");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void setupClientEnvironment() {
        String userHome = System.getProperty("user.home");
        try {
            File configFile = new File(userHome + File.separator + ".tooltester" + File.separator + "config");
            this.config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            exceptionMessage(e, "", API_ERROR);
        }

        // pull out the variables from the config
        String token = config.getString("token", "");
        String serverUrl = config.getString("server-url", "https://www.dockstore.org:8443");

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
        } catch (ApiException ex) {
            this.isAdmin = false;
        }

        defaultApiClient.setDebugging(DEBUG.get());
    }

    /**
     * Finalize the results of testing
     */
    private void finalizeResults() {
    }

    /**
     * do the actual work here
     */
    private void run() {
        setupClientEnvironment();

        List<Tool> verifiedTools;
        boolean toolTestResult = false;
        /** use swagger-generated classes to talk to dockstore */
        try {
            final List<Tool> tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);
            System.out.println("Number of tools on Dockstore: " + tools.size());
            LongStream longStream = tools.parallelStream().filter(Tool::getVerified)
                    .mapToLong(tool -> tool.getVersions().parallelStream().filter(ToolVersion::getVerified).count());
            System.out.println("Number of versions of tools to test on Dockstore (currently): " + longStream.sum());
            toolTestResult = tools.parallelStream().filter(Tool::getVerified).map(this::testTool).reduce(true, Boolean::logicalAnd);
            System.out.println("Successful \"testing\" of tools found on Dockstore: " + toolTestResult);
            List<String> verifiedSources = Arrays.asList("Docktesters group", "Another Group");
            verifiedTools = getVerifiedTools();
            for (Tool verifiedTool : verifiedTools) {
                printAllFilesFromTool(verifiedTool);
            }
            System.out.println("Number of combinations to test: " + count);
            count = 0;
            verifiedTools = getVerifiedTools(verifiedSources);
            for (Tool verifiedTool : verifiedTools) {
                printAllFilesFromTool(verifiedTool);
            }
            System.out.println("Number of combinations to test: " + count);


        } catch (ApiException e) {
            exceptionMessage(e, "", API_ERROR);
        }

        finalizeResults();

    }

    /**
     * This function prints all the files from the verified tool
     * @param verifiedTool  The verified tool
     */
    private void printAllFilesFromTool(Tool verifiedTool) throws ApiException {
        SourceFile dockerfile;
        SourceFile descriptor;
        SourceFile testParameter;
        List<SourceFile> testParameterFiles;
        DockstoreTool dockstoreTool;
        Long containerId;
        dockstoreTool = containersApi.getPublishedContainerByToolPath(verifiedTool.getId());
        containerId = dockstoreTool.getId();
        for (ToolVersion version : verifiedTool.getVersions()){
            String tag = version.getName();
            dockerfile = containersApi.dockerfile(containerId, tag);
            for (ToolVersion.DescriptorTypeEnum descriptorType : version.getDescriptorType()){
                switch(descriptorType.toString()) {
                case "CWL":
                    descriptor = containersApi.cwl(containerId, tag);
                    testParameterFiles = containersApi.getTestParameterFiles(containerId, tag, descriptorType.toString());

                    for (SourceFile testParameterFile : testParameterFiles) {
                        testParameter = testParameterFile;
                        sendToJenkins(dockerfile, descriptor, testParameter, verifiedTool);
                    }
                    break;
                case "WDL":
                    descriptor = containersApi.wdl(containerId, tag);
                    testParameterFiles = containersApi.getTestParameterFiles(containerId, tag, descriptorType.toString());
                    for (SourceFile testParameterFile : testParameterFiles) {
                        testParameter = testParameterFile;
                        sendToJenkins(dockerfile, descriptor, testParameter, verifiedTool);
                    }
                    break;
                default:
                    break;
                }
            }

        }

    }

    /**
     * This function is supposed to send the 3 source files to jenkins and run them
     * @param dockerFile    The dockerfile
     * @param descriptor    The descript
     * @param testParameter The test parameter f
     */
    private void sendToJenkins(SourceFile dockerFile, SourceFile descriptor, SourceFile testParameter, Tool tool){
        count += 1;
    }

//    /**
//     * This function saves the docker image
//     *
//     * @param path
//     * @throws Exception
//     */
//    public void saveImage(String path) {
//        try {
//
//            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withRegistryUrl("https://quay.io").build();
//            DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
//            dockerClient.inspectImageCmd(path);
//            dockerClient.pullImageCmd(path).exec(new PullImageResultCallback()).awaitSuccess();
//            SaveImageCmd imageCmd = dockerClient.saveImageCmd(path);
//            InputStream inputStream = imageToInputStream(dockerClient, path);
//            InputStream image = IOUtils.toBufferedInputStream(inputStream);
//            File targetFile = new File("tooltester/src/main/resources/targetFile.tar");
//            FileUtils.copyInputStreamToFile(image, targetFile);
//        } catch (IOException e) {
//            exceptionMessage(e, "IO ERROR", IO_ERROR);
//        } catch (Exception e) {
//            exceptionMessage(e, "Something went wrong", CLIENT_ERROR);
//        }
//    }
//
//    private InputStream imageToInputStream(DockerClient dockerClient, String img) {
//        InputStream inputStream = null;
//        try {
//            inputStream = dockerClient.saveImageCmd(img).exec();
//        } catch (DockerException e) {
//            throw new RuntimeException("Could not save Docker image to an input stream");
//        }
//        return inputStream;
//    }

    /**
     * This function checks if the any of the tool's verified source matches the filter
     * @param filter            The list of verified sources that we're interested in
     * @param verifiedSources    The tool version's verified sources
     * @return                  True if the one of the verified sources matches the filter
     */
    private boolean matchVerifiedSource(List<String> filter, String verifiedSources){
        return filter.stream().anyMatch(str -> str.trim().equals(verifiedSources));
        
    }

    /**
     * Gets the list of verified tools
     * @return  The list of verified tools
     */
    private List<Tool> getVerifiedTools() {
        List<Tool> verifiedTools = null;
        try {
            final List<Tool> tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);
            verifiedTools = tools.parallelStream().filter(Tool::getVerified).collect(Collectors.toList());
            for (Tool tool : verifiedTools){
                getVerifiedToolVersions(tool);
            }
        } catch (ApiException e) {
            exceptionMessage(e, "", API_ERROR);
        }
        return verifiedTools;
    }

    /**
     * Gets the list of verified tools and applies filter to it
     * @param verifiedSources   Filter parameter to filter the verified sources
     * @return                  The list of verified tools
     */
    private List<Tool> getVerifiedTools(List<String> verifiedSources) {
        List<Tool> verifiedTools = getVerifiedTools();
        for (Tool tool : verifiedTools){
            filterSource(tool, verifiedSources);
        }
        return verifiedTools;
    }

    /**
     * Removes all the non-verified versions from the tool
     * @param tool  The verified tool
     * @return      The verified tool with non-verified versions removed
     */
    private void getVerifiedToolVersions(Tool tool) {
        tool.setVersions(tool.getVersions().parallelStream().filter(ToolVersion::getVerified).collect(Collectors.toList()));
    }

    /**
     * Removes all the versions that do not match the sources filter
     * @param tool              The verified tool
     * @param verifiedSources   The verified sources filter
     * @return
     */
    private void filterSource(Tool tool, List<String> verifiedSources) {
        tool.setVersions(tool.getVersions().parallelStream().filter(p -> matchVerifiedSource(verifiedSources, p.getVerifiedSource())).collect(Collectors.toList()));
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
     * @param tool
     * @return
     */
    private boolean testTool(Tool tool) {
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
}
