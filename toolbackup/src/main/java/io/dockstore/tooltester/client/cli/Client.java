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
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.System.err;
import static java.lang.System.out;

public class Client {

    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private final OptionSet options;
    private ContainersApi containersApi;
    private UsersApi usersApi;
    private GAGHApi ga4ghApi;
    private boolean isAdmin = false;

    public static final int API_ERROR = 6; // API throws an exception

    private HierarchicalINIConfiguration config;

    public Client(OptionSet options) {
        this.options = options;
    }

    /**
     * Main method
     * @param argv
     */
    public static void main(String[] argv) {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> url = parser.accepts("dockstore-url", "dockstore install that we wish to test tools from").withRequiredArg().defaultsTo("");
        ArgumentAcceptingOptionSpec<String> pathConfig = parser.accepts("config-local-dir", "determines where to backup tools (MUST END WITH /)").withRequiredArg().defaultsTo(".").ofType(String.class);

        final OptionSet options = parser.parse(argv);

        Client client = new Client(options);
        client.run(options.valueOf(pathConfig));

        out.println("url: " + options.valueOf(url));
        out.println("path config: " + options.valueOf(pathConfig));
    }

    protected void setupClientEnvironment() {
        String userHome = System.getProperty("user.home");
        try {
            File configFile = new File(userHome + File.separator + ".tooltester" + File.separator + "config");
            this.config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
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

        defaultApiClient.setDebugging(ErrorExit.DEBUG.get());
    }

    /**
     * Generate report based on saved local Docker images
     * @param basePath refers to the local directory in which docker images will be saved
     */
    private void run(String basePath) {
        report(save(basePath), basePath);
    }

    private long getTotalSizeInB(Map<String, List<VersionDetail>> toolNameToList) {
        long totalSize = 0;
        for (Map.Entry<String, List<VersionDetail>> entry : toolNameToList.entrySet()) {
            for(VersionDetail row: entry.getValue()) {
                totalSize += row.getSize();
            }
        }
        return totalSize;
    }

    private void report(Map<String, List<VersionDetail>> toolNameToList, String basePath) {
        try {
            for (Map.Entry<String, List<VersionDetail>> entry : toolNameToList.entrySet()) {
                /** Generates html page listing all the versions of the current tool */
                final String toolPagePath = basePath+entry.getKey()+".html";
                FileUtils.writeStringToFile(new File(toolPagePath), ReportGenerator.generateFilesReport(entry.getValue()), "UTF-8");
                out.println("Finished generating " + toolPagePath);
            }
            /** Generates main menu */
            FileUtils.writeStringToFile(new File(basePath + "index.html"), ReportGenerator.generateMenu(toolNameToList.keySet(), getTotalSizeInB(toolNameToList)), "UTF-8");

            /** Generates JSON map */
            ReportGenerator.generateJSONMap(toolNameToList, basePath);
        } catch(IOException e) {
            throw new RuntimeException("Could not generate complete report");
        }
    }

    private boolean pullDockerImage(DockerClient docker, String img) {
        boolean pulled = true;
        try {
            docker.pull(img);
            out.println("Pulled image: " + img);
        } catch (DockerException e) {
            // not able to pull image
            pulled = false;
            err.println("Unable to pull " + img);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while pulling " + img);
        }
        return pulled;
    }

    private InputStream saveDockerImage(DockerClient docker, String img) {
        InputStream inputStream = null;
        try {
            inputStream = docker.save(img);
        } catch (DockerException | IOException | InterruptedException e) {
            throw new RuntimeException("Could not save Docker image to an input stream");
        }
        return  inputStream;
    }

    /**
     * Save docker images to a local directory
     * @param basePath
     */
    private Map<String, List<VersionDetail>> save(String basePath) {
        /** use swagger-generated classes to talk to dockstore */
        setupClientEnvironment();

        InputStream inputStream;

        Map<String, List<VersionDetail>> toolNameToList = ReportGenerator.loadJSONMap(basePath);

        try {
            final DockerClient docker = DefaultDockerClient.fromEnv().build();

            /** removed final modifier for testing purposes only */
            final List<Tool> tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);

            /** only look at 2 elements for testing purposes only
            final int fromIndex = 40;
            final int toIndex = tools.size();
            tools = tools.subList(fromIndex, toIndex); */

            String toolName, dirPath, img, versionId, versionTag, date, filePath;

            for(Tool tool : tools)  {
                toolName = tool.getToolname();
                dirPath = basePath + tool.getId();
                DirectoryGenerator.createDirectory(dirPath);

                if(toolName == null) {
                    continue;
                }

                List<ToolVersion> versions = tool.getVersions();
                List<VersionDetail> listOfVersions;

                if(toolNameToList.containsKey(toolName)) {
                    listOfVersions = toolNameToList.get(toolName);
                } else {
                    listOfVersions = new ArrayList<VersionDetail>();
                }

                for(ToolVersion version : versions) {
                    img = version.getImage();

                    if(img == null) {
                        continue;
                    }

                    versionId = version.getId();
                    versionTag = versionId.substring(versionId.lastIndexOf(":") + 1);
                    date = version.getMetaVersion();
                    filePath = dirPath + "/" + versionTag + ".tar";

                    if(pullDockerImage(docker, img)) {
                        /** Save docker image to a .tar file and have the filename match its tag */
                        inputStream = saveDockerImage(docker, img);

                        File newFile = new File(filePath);
                        FileUtils.copyInputStreamToFile(inputStream, newFile);
                        out.println("Created new file: " + filePath);
                        listOfVersions.add(new VersionDetail(versionTag, date, newFile.length(), FormattedTimeGenerator.getFormattedCreationTime(newFile), true));
                    } else {
                        listOfVersions.add(new VersionDetail(versionTag, date, 0, null, false));
                    }
                }
                toolNameToList.put(toolName, listOfVersions);
            }
            docker.close();
            out.println("Docker closed");
        } catch (ApiException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        } catch (IOException e) {
            ErrorExit.exceptionMessage(e, "Could not copy Docker image to file", 1);
        } catch (DockerCertificateException e) {
            ErrorExit.exceptionMessage(e, "DockerCertificateException occurred while trying to set up DockerClient", 1);
        }
        return toolNameToList;
    }
}
