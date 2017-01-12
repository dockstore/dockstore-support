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

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.GAGHApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


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
     * @param argv
     */
    public static void main(String[] argv) {
        OptionParser parser = new OptionParser();
        parser.accepts("dockstore-url", "dockstore install that we wish to test tools from").withRequiredArg().defaultsTo("");
        parser.accepts("runtime-environment", "determines where to test tools").withRequiredArg().defaultsTo("local");
        ArgumentAcceptingOptionSpec<String> config = parser.accepts("config", "determines where to backup tools MUST END WITH /").withRequiredArg().defaultsTo(".").ofType(String.class);

        final OptionSet options = parser.parse(argv);

        Client client = new Client(options);
        client.run(options.valueOf(config));
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
     * Generate report based on saved local Docker images
     * @param basePath refers to the local directory in which docker images will be saved
     */
    private void run(String basePath) {
        report(save(basePath), basePath);
    }

    private double getTotalSizeInB(Map<String, List<VersionDetail>> toolNameToList) {
        double totalSize = 0;
        for (Map.Entry<String, List<VersionDetail>> entry : toolNameToList.entrySet()) {
            for(VersionDetail row: entry.getValue()) {
                totalSize += row.getSize();
            }
        }
        return totalSize;
    }

    private void createFileFromString(String input, String path) {
        FileGenerator.inputStreamToFile(InputStreamGenerator.stringToInputStream(input), path);
    }

    private void report(Map<String, List<VersionDetail>> toolNameToList, String basePath) {
        for (Map.Entry<String, List<VersionDetail>> entry : toolNameToList.entrySet()) {
            /** Generates html page listing all the versions of the current tool */
            createFileFromString(ReportGenerator.generateFilesReport(entry.getValue()), basePath+entry.getKey()+".html");
        }
        /** Generates main menu */
        createFileFromString(ReportGenerator.generateMenu(toolNameToList.keySet(), getTotalSizeInB(toolNameToList)), basePath+"index.html");
        /** Generates JSON map */
        createFileFromString(ReportGenerator.generateJSONMap(toolNameToList), basePath+"JSONMap.txt");
    }

    /**
     * Save docker images to a local directory
     * @param basePath
     */
    private Map<String, List<VersionDetail>> save(String basePath) {
        /** use swagger-generated classes to talk to dockstore */
        setupClientEnvironment();

        InputStream inputStream;
        Map<String, List<VersionDetail>> toolNameToList = new HashMap<>();

        try {
            final DockerClient docker = DefaultDockerClient.fromEnv().build();
            double totalSizeInBytes = 0, fileSizeInBytes;

            /** removed final modifier for testing purposes only */
            final List<Tool> tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);

            /** only look at 2 elements for testing purposes only */
//            final int fromIndex = 2;
//            final int toIndex = 4;
//            tools = tools.subList(fromIndex, toIndex);

            String toolName, dirPath, img, versionId, versionTag, date;

            final int indexAfterDate = 10;

            for(Tool tool : tools)  {
                toolName = tool.getToolname();
                dirPath = basePath + tool.getId();
                List<ToolVersion> versions = tool.getVersions();
                List<VersionDetail> listOfVersions = new ArrayList<VersionDetail>();

                DirectoryGenerator.createDirectory(dirPath);

                for(ToolVersion version : versions) {
                    img = version.getImage();
                    if(img != "null") {
                        docker.pull(img);
                    }
                    /** Save docker image to a .tar file and have the filename match its tag */
                    inputStream = docker.save(img);
                    versionId = version.getId();
                    versionTag = versionId.substring(versionId.lastIndexOf(":") + 1);
                    date = version.getMetaVersion().substring(0, indexAfterDate);
                    File outputFile = FileGenerator.inputStreamToFile(inputStream, dirPath + "/" + versionTag + ".tar");

                    fileSizeInBytes = outputFile.length();
                    listOfVersions.add(new VersionDetail(versionTag, date, fileSizeInBytes, FormattedTimeGenerator.getFormattedCreationTime(outputFile)));
                }
                toolNameToList.put(toolName, listOfVersions);
            }
            docker.close();
        } catch (ApiException e) {
            exceptionMessage(e, "", API_ERROR);
        } catch (DockerCertificateException|DockerException|InterruptedException|IOException e) {
            exceptionMessage(e, e.getMessage(), 1);
        }
        return toolNameToList;
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
