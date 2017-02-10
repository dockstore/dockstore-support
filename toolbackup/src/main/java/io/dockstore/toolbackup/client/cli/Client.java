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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
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
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.lang.System.out;

public class Client {
    private static final Logger ROOT_LOGGER = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final OptionSet options;
    private ContainersApi containersApi;
    private UsersApi usersApi;
    private GAGHApi ga4ghApi;
    private boolean isAdmin = false;
    private String endpoint;

    public static final int GENERIC_ERROR = 1;          // General error, not yet described by an error type
    public static final int CONNECTION_ERROR = 150;     // Connection exception
    public static final int IO_ERROR = 3;               // IO throws an exception
    public static final int API_ERROR = 6;              // API throws an exception
    public static final int CLIENT_ERROR = 4;           // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10;         // Command is not successful, but not due to errors

    // dockerstor_tutorial [42, 43)
    private static final int FROM_INDEX = 4;
    private static final int TO_INDEX = 5;
    private static final LocalDateTime TIME_NOW = LocalDateTime.now();
    private static String stringTime;

    private Map<String, List<VersionDetail>> toolsToVersions;
    private HierarchicalINIConfiguration config;

    public Client(OptionSet options) {
        this.options = options;
    }

    static {
        ROOT_LOGGER.setLevel(Level.WARN);
        stringTime = FormattedTimeGenerator.getFormattedTimeNow(TIME_NOW);
    }

    public static void main(String[] argv) {
        out.println("Back-up script started: " + stringTime);

        out.println(Arrays.toString(argv));

        OptionParser parser = new OptionParser();
        final ArgumentAcceptingOptionSpec<String> bucketName = parser.accepts("bucket-name", "bucket to which files will be backed-up").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> keyPrefix = parser.accepts("key-prefix", "key prefix of bucket (ex. client)").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> localDir = parser.accepts("local-dir", "local directory to which files will be backed-up").withRequiredArg().defaultsTo(".").ofType(String.class);
        final ArgumentAcceptingOptionSpec<Boolean> isTestMode = parser.accepts("test-mode-activate", "if true test mode is activated").withRequiredArg().ofType(Boolean.class);

        final OptionSet options = parser.parse(argv);
        Client client = new Client(options);

        String local = options.valueOf(localDir);
        String dirPath = Paths.get(local).toAbsolutePath().toString();
        DirectoryGenerator.createDir(dirPath);

        try {
            client.run(dirPath, options.valueOf(bucketName), options.valueOf(keyPrefix), options.valueOf(isTestMode));
        }  catch (UnknownHostException e) {
            ErrorExit.exceptionMessage(e, "No internet access", CONNECTION_ERROR);
        }

        final LocalDateTime end = LocalDateTime.now();
        FormattedTimeGenerator.elapsedTime(TIME_NOW, end);
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

    private List<File> getFilesForUpload(String bucketName, String prefix, String baseDir, S3Communicator s3Communicator) {
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

    private void run(String baseDir, String bucketName, String keyPrefix, boolean isTestMode) throws UnknownHostException {
        // use swagger-generated classes to talk to dockstore
        setupClientEnvironment();
        List<Tool> tools = getTools();
        if(isTestMode) {
            tools = tools.subList(FROM_INDEX, TO_INDEX);
        }

        final S3Communicator s3Communicator= new S3Communicator("dockstore", endpoint);
        String reportDir = baseDir + File.separator + "report";

        // save Docker images to local
        saveToLocal(baseDir, reportDir, tools, new DockerCommunicator());
        // just the Docker images
        List<File> forUpload = getFilesForUpload(bucketName, keyPrefix, baseDir, s3Communicator);
        long addedTotalInB = getFilesTotalSizeB(forUpload);

        // report
        long cloudTotalInB = s3Communicator.getCloudTotalInB(bucketName, keyPrefix);
        report(reportDir, addedTotalInB, cloudTotalInB);

        // upload to cloud
        // NOTE: cannot invoke getFilesForUpload again as sometimes the report size may be exactly the same but the contents will be different
        forUpload.addAll(FileUtils.listFiles(new File(reportDir), new String[] { "html", "JSON", "json" }, false));

        out.println("Files to be uploaded: " + Arrays.toString(forUpload.toArray()));

        s3Communicator.uploadDirectory(bucketName, keyPrefix, baseDir, forUpload, true);

        s3Communicator.shutDown();
    }


    //-----------------------Report-----------------------
    public long getFilesTotalSizeB(List<File> forUpload) {
        long totalSize = 0;
        for (File file : forUpload) {
            totalSize += file.length();
        }
        return totalSize;
    }

    private void report(String baseDir, long addedTotalInB, long cloudTotalInB) {
        try {
            for (Map.Entry<String, List<VersionDetail>> entry : toolsToVersions.entrySet()) {
                // each tool has its own page for its versions
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
    public void saveDockerImage(String img, File file, DockerCommunicator dockerCommunicator) {
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

    private void update(ToolVersion version, String dirPath, List<VersionDetail> versionsDetails, String img, DockerCommunicator dockerCommunicator) {
        String versionId = version.getId();
        String versionTag = versionId.substring(versionId.lastIndexOf(":") + 1);
        String metaVersion = version.getMetaVersion();
        VersionDetail before;

        if(dockerCommunicator.pullDockerImage(img)) {
            // docker img valid
            long dockerSize = dockerCommunicator.getImageSize(img);
            File imgFile = new File(dirPath + File.separator + versionTag + ".tar");

            // check if the script had encountered this image before
            before = findLocalVD(versionsDetails, versionTag);

            // image had not changed from the last encounter
            if(before != null && before.getDockerSize() == dockerCommunicator.getImageSize(img)) {
                out.println(img + " did not change");
                before.addTime(stringTime);

                long fileSize = imgFile.length();
                // image not yet saved in local
                if(!imgFile.isFile() || fileSize != before.getFileSize()) {
                    out.println("However, a local file must be created for " + img);
                    saveDockerImage(img, imgFile, dockerCommunicator);
                }
            } else {
                // new version of image
                saveDockerImage(img, imgFile, dockerCommunicator);

                if(before != null) {
                    out.println(img + " had changed");
                    before.setPath("");
                }

                versionsDetails.add(new VersionDetail(versionTag, metaVersion, dockerSize, imgFile.length(), stringTime, true, imgFile.getAbsolutePath()));
            }

        } else {
            // non-existent image
            before = findInvalidVD(versionsDetails, versionTag);
            if(before != null) {
                before.addTime(stringTime);
            } else {
                versionsDetails.add(new VersionDetail(versionTag, metaVersion, 0, 0, stringTime, false, ""));
            }
        }
    }

    public void saveToLocal(String baseDir, String reportDir, final List<Tool> tools, DockerCommunicator dockerCommunicator) {
        toolsToVersions = ReportGenerator.loadJSONMap(reportDir);

        for(Tool tool : tools)  {
            String toolName = tool.getToolname();
            String dirPath = baseDir + File.separator + tool.getId();
            DirectoryGenerator.createDir(dirPath);
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
                update(version, dirPath, versionsDetails, img, dockerCommunicator);
            }
            toolsToVersions.put(toolName, versionsDetails);
        }
        dockerCommunicator.closeDocker();
        out.println("Closed docker client");
    }

    //-----------------------Set up to connect to GA4GH API-----------------------
    protected void setupClientEnvironment() {
        String userHome = System.getProperty("user.home");
        try {
            File configFile = new File(userHome + File.separator + ".toolbackup" + File.separator + "config.ini");
            this.config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }

        // pull out the variables from the config
        String token = config.getString("token", "");
        String serverUrl = config.getString("server-url", "https://www.dockstore.org:8443");
        endpoint = config.getString("endpoint");

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
            this.isAdmin = false;
        }
        defaultApiClient.setDebugging(ErrorExit.DEBUG.get());
    }

    public ContainersApi getContainersApi() {
        return containersApi;
    }
}
