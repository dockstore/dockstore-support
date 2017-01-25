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
package io.dockstore.toolbackup.client.cli;

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
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.System.out;

public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private final OptionSet options;
    private ContainersApi containersApi;
    private UsersApi usersApi;
    private GAGHApi ga4ghApi;
    private boolean isAdmin = false;

    public static final int GENERIC_ERROR = 1;          // General error, not yet described by an error type
    public static final int CONNECTION_ERROR = 150;     // Connection exception
    public static final int IO_ERROR = 3;               // IO throws an exception
    public static final int API_ERROR = 6;              // API throws an exception
    public static final int CLIENT_ERROR = 4;           // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10;         // Command is not successful, but not due to errors

    // dockerstor_tutorial [42, 43)
    private static final int FROM_INDEX = 42;
    private static final int TO_INDEX = 43;

    private final S3Communicator s3Communicator= new S3Communicator();
    private final DockerCommunicator dockerCommunicator = new DockerCommunicator();
    private Map<String, List<VersionDetail>> toolsToVersions;
    private HierarchicalINIConfiguration config;

    public Client(OptionSet options) {
        this.options = options;
    }

    public static void main(String[] argv) {
        final OptionParser parser = new OptionParser();
        final ArgumentAcceptingOptionSpec<String> bucketName = parser.accepts("bucket-name", "bucket to which files will be backed-up").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> keyPrefix = parser.accepts("key-prefix", "key prefix of bucket (ex. client)").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> localDir = parser.accepts("local-dir", "local directory to which files will be backed-up").withRequiredArg().defaultsTo(".").ofType(String.class);
        final ArgumentAcceptingOptionSpec<Boolean> isTestMode = parser.accepts("test-mode-activate", "if true test mode is activated").withRequiredArg().ofType(Boolean.class);

        final OptionSet options = parser.parse(argv);

        String local = options.valueOf(localDir);
        String dirPath = Paths.get(local).toAbsolutePath().toString();
        DirectoryGenerator.validatePath(dirPath);

        Client client = new Client(options);
        client.run(dirPath, options.valueOf(bucketName), options.valueOf(keyPrefix), options.valueOf(isTestMode));
    }

    //-----------------------Main invocations-----------------------
    private List<Tool> getTools() {
        List<Tool> tools = null;
        try {
            tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            ErrorExit.exceptionMessage(e, "Could not retrieve dockstore tools", API_ERROR);
        }
        return tools;
    }

    private List<File> getModifiedFiles(String key, final Map<String, Long> keysToSizes, File file) {
        List<File> modifiedFiles = new ArrayList<>();
        for(Map.Entry<String, Long> entry: keysToSizes.entrySet()) {
            if (key == entry.getKey()) {
                if (entry.getValue() != file.length()) {
                    modifiedFiles.add(file);
                }
            }
        }
        return modifiedFiles;
    }

    private List<File> getFilesForUpload(String bucketName, String prefix, String baseDir) {
        List<File> uploadList = new ArrayList<>();
        List<File> localFiles = (List<File>) FileUtils.listFiles(new File(baseDir), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        Map<String, Long> keysToSizes = s3Communicator.getKeysToSizes(bucketName, prefix);

        for(File file : localFiles) {
            String key = prefix + file.getAbsolutePath().replace(baseDir, "");
            if(!keysToSizes.containsKey(key)) {
                out.println("Cloud does not yet have this file: " + file.getAbsolutePath());
                uploadList.add(file);
            } else {
                if(keysToSizes.get(key) != file.length()) {
                    out.println("Updated " + file.getAbsolutePath() + " has not yet been uploaded");
                    getModifiedFiles(key, keysToSizes, file);
                }
            }
        }

        return uploadList;
    }

    private void run(String baseDir, String bucketName, String keyPrefix, boolean isTestMode) {
        // use swagger-generated classes to talk to dockstore
        setupClientEnvironment();
        List<Tool> tools = getTools();
        if(isTestMode) {
            tools = tools.subList(FROM_INDEX, TO_INDEX);
        }

        saveToLocal(baseDir, tools);
        // just the Docker images
        List<File> forUpload = getFilesForUpload(bucketName, keyPrefix, baseDir);

        long addedTotalInB = getAddedSizeInB(forUpload);
        long cloudTotalInB = s3Communicator.getCloudTotalInB(bucketName, keyPrefix);

        String reportDir = baseDir + "/report";

        report(reportDir, addedTotalInB, cloudTotalInB);

        // Docker images + report
        // NOTE: cannot invoke getFilesForUpload again as sometimes the report size may be exactly the same but the contents will be different
        forUpload.addAll((List<File>) FileUtils.listFiles(new File(reportDir), new String[] { "html", "JSON" }, false));
        s3Communicator.uploadDirectory(bucketName, keyPrefix, baseDir, forUpload);

        s3Communicator.shutDown();
    }


    //-----------------------Report-----------------------
    private long getAddedSizeInB(List<File> forUpload) {
        long totalSize = 0;
        for (File file : forUpload) {
            totalSize += file.length();
        }
        return totalSize;
    }

    private void report(String baseDir, long addedTotalInB, long cloudTotalInB) {
        try {
            for (Map.Entry<String, List<VersionDetail>> entry : toolsToVersions.entrySet()) {
                // each tool has it's own page for its versions
                final String toolReportPath = baseDir + File.separator + entry.getKey()+".html";
                FileUtils.write(new File(toolReportPath), ReportGenerator.generateToolReport(entry.getValue()), "UTF-8");
                out.println("Finished creating " + toolReportPath);
            }
            // main menu
            FileUtils.writeStringToFile(new File(baseDir + File.separator + "index.html"),
                                        ReportGenerator.generateMainMenu(toolsToVersions.keySet(), addedTotalInB, cloudTotalInB), "UTF-8");
            out.println("Finished creating index.html");

            // json map
            ReportGenerator.generateJSONMap(toolsToVersions, baseDir);
        } catch(IOException e) {
            ErrorExit.exceptionMessage(e, "Could not write report to local directory", IO_ERROR);
        }
    }

    //-----------------------Save to local-----------------------
    public void saveDockerImage(String img, File file) {
        try {
            FileUtils.copyInputStreamToFile(dockerCommunicator.saveDockerImage(img), file);
            out.println("Created new file: " + file);
        } catch (IOException e) {
            ErrorExit.exceptionMessage(e, "Could save Docker image to the file " + file, IO_ERROR);
        }
    }

    private VersionDetail findLocalVD(List<VersionDetail> versionsDetails, String version) {
        for(VersionDetail row : versionsDetails) {
            if(row.getVersion().equals(version) && row.getPath() != "") {
                return row;
            }
        }
        return null;
    }

    private VersionDetail findInvalidVD(List<VersionDetail> versionsDetails, String version) {
        for(VersionDetail row : versionsDetails) {
            if(row.getVersion().equals(version) && !row.isValid()) {
                return row;
            }
        }
        return null;
    }

    private void update(ToolVersion version, String dirPath, List<VersionDetail> versionsDetails, String img) {
        String versionId = version.getId();
        String versionTag = versionId.substring(versionId.lastIndexOf(":") + 1);
        String metaVersion = version.getMetaVersion();
        String timeOfExecution = FormattedTimeGenerator.getFormattedTimeNow();
        VersionDetail before;

        if(dockerCommunicator.pullDockerImage(img)) {
            // docker img valid
            String filePath = dirPath + File.separator + versionTag + ".tar";
            long dockerSize = dockerCommunicator.getImageSize(img);
            File imgFile = new File(filePath);

            before = findLocalVD(versionsDetails, versionTag);
            if(before != null && before.getDockerSize() == dockerCommunicator.getImageSize(img)) {
                out.println(img + " did not change");
                before.addTime(timeOfExecution);

                long fileSize = imgFile.length();
                if(!imgFile.isFile() || fileSize != before.getFileSize()) {
                    out.println("However, a local file must be created for " + img);
                    saveDockerImage(img, imgFile);
                }
            } else {
                saveDockerImage(img, imgFile);

                if(before != null) {
                    out.println(img + " had changed");
                    before.setPath("");
                }

                versionsDetails.add(new VersionDetail(versionTag, metaVersion, dockerSize, imgFile.length(), timeOfExecution, true, imgFile.getAbsolutePath()));
            }

        } else {
            // docker image does not exist on quay.io
            before = findInvalidVD(versionsDetails, versionTag);
            if(before != null) {
                before.addTime(timeOfExecution);
            } else {
                versionsDetails.add(new VersionDetail(versionTag, metaVersion, 0, 0, timeOfExecution, false, ""));
            }
        }
    }

    public void saveToLocal(String baseDir, final List<Tool> tools) {
        toolsToVersions = ReportGenerator.loadJSONMap(baseDir + "/report");

        for(Tool tool : tools)  {
            String toolName = tool.getToolname();
            String dirPath = baseDir + File.separator + tool.getId();
            DirectoryGenerator.validatePath(dirPath);
            if(toolName == null) {
                continue;
            }

            List<VersionDetail> versionsDetails;
            if(toolsToVersions.containsKey(toolName)) {
                versionsDetails = toolsToVersions.get(toolName);
            } else {
                versionsDetails = new ArrayList<>();
            }

            List<ToolVersion> versions = tool.getVersions();
            for(ToolVersion version : versions) {
                String img = version.getImage();
                if(img == null) {
                    continue;
                }
                update(version, dirPath, versionsDetails, img);
            }
            toolsToVersions.put(toolName, versionsDetails);
        }
        dockerCommunicator.closeDocker();
        out.println("Closed docker client");
    }

    //-----------------------Set up-----------------------
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

    public ContainersApi getContainersApi() {
        return containersApi;
    }
}
