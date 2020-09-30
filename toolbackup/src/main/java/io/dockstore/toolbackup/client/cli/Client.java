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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Writer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static java.lang.System.out;

public class Client {
    private static final Logger ROOT_LOGGER = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final OptionSet options;
    private ContainersApi containersApi;
    private WorkflowsApi workflowsApi;
    private UsersApi usersApi;
    private Ga4GhApi ga4ghApi;
    private boolean isAdmin = false;
    private String endpoint;

    public static final int GENERIC_ERROR = 1;          // General error, not yet described by an error type
    public static final int CONNECTION_ERROR = 150;     // Connection exception
    public static final int IO_ERROR = 3;               // IO throws an exception
    public static final int API_ERROR = 6;              // API throws an exception
    public static final int CLIENT_ERROR = 4;           // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10;         // Command is not successful, but not due to errors

    // retrieves only the bamstats tool as shown in https://dockstore.org/docs/getting-started-with-docker
    private static final String BAMSTATS = "quay.io/briandoconnor/dockstore-tool-bamstats";

    private static final LocalDateTime TIME_NOW = LocalDateTime.now();
    private static String stringTime;

    private Map<String, List<VersionDetail>> toolsToVersions;
    private HierarchicalINIConfiguration config;

    public long totalToolVersionImageSize        = 0;
    public long totalWorkflowVersionImageSize    = 0;
    public long checkerWorkflowCounter           = 0;
    public long publishedWorkflowsCounter        = 0;
    public long toolCounter                      = 0;
    public long publishedWorkflowVersionsCounter = 0;
    public long toolVersionCounter               = 0;
    public long workflowImagesNonEmptyObjCounter = 0;
    public long workflowImagesEmptyObjectCounter = 0;
    public long workflowImages204ResponseCounter = 0;
    public long workflowImages500ResponseCounter = 0;
    public long nonDockerHubImageRefCounter      = 0;
    public long dockerHubImageRefCounter         = 0;
    public long parsedToolCounter                = 0;
    public long parsedNonDupeToolCounter         = 0;

    private Map<String, DockerHubImageDetail> dockerHubImagesMap        = new HashMap<>();
    private Map<String, NonDockerHubImageDetail> nonDockerHubImagesMap  = new HashMap<>();

    public Client(OptionSet options) {
        this.options = options;
    }

    static {
        ROOT_LOGGER.setLevel(Level.WARN);
        stringTime = FormattedTimeGenerator.getFormattedTimeNow(TIME_NOW);
    }

    public static void main(String[] argv) throws IOException {
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
        out.println("Back-up script completed successfully in " + FormattedTimeGenerator.elapsedTime(TIME_NOW, end));
    }

    //-----------------------Main invocations-----------------------
    private List<Tool> getTools() {
        List<Tool> tools = null;
        try {
            tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            ErrorExit.exceptionMessage(e, "Could not retrieve dockstore tools", API_ERROR);
        }
        return tools;
    }

    private List<Workflow> getWorkflows(String offset) {
        List<Workflow> workflows = null;
        try {
            workflows = workflowsApi.allPublishedWorkflows(offset, null, null, null, null, null);
        } catch (ApiException e) {
            ErrorExit.exceptionMessage(e, "Could not retrieve dockstore workflows", API_ERROR);
        }
        return workflows;
    }

    private String getWorkflowTools(Long workflowID, Long workflowVersionID, String trsID) {
        String tools = null;
        try {
            System.out.println(workflowID + " " + workflowVersionID);
            tools = workflowsApi.getTableToolContent(workflowID, workflowVersionID);
        } catch (ApiException e) {
            System.out.println(e);
            workflowImages500ResponseCounter++;
            logData("./workflowVersionsWith500Response.csv", "/workflows/" + workflowID + "/tools/" + workflowVersionID + "&" + trsID + "&" + e);
            return "failed to retrieve workflow tools";
        }
        return tools;
    }

    private boolean isNonDockerHubImage(String dockerImage, String imageRepoLink, String trsID) {
        if (dockerImage.split("/").length  > 2) {
            if (nonDockerHubImagesMap.containsKey(dockerImage)) {
                nonDockerHubImagesMap.get(dockerImage).appendToRepetitionList(trsID);
                return true;
            }
            nonDockerHubImageRefCounter++;
            System.out.println("Non DockerHub Image: " + dockerImage);
            if (imageRepoLink.contains("https://hub.docker.com/_/")) {
                imageRepoLink = "";
            }
            nonDockerHubImagesMap.put(dockerImage, new NonDockerHubImageDetail(dockerImage, imageRepoLink, trsID, new ArrayList<>()));
            return true;
        }
        return false;
    }

    private boolean isDuplicateDockerHubImage(String dockerHubImage, String trsID) {
        if (dockerHubImagesMap.containsKey(dockerHubImage)) {
            System.out.println("Repeated Image: " + dockerHubImage);
            if (dockerHubImagesMap.get(dockerHubImage) != null) {
                dockerHubImagesMap.get(dockerHubImage).appendToRepetitionList(trsID);
            }
            return true;
        }
        return false;
    }

    private List<Tool> getTestTool(String id) {
        List<Tool> tools = new ArrayList<>();
        try {
            tools.add(ga4ghApi.toolsIdGet(id));
        } catch (ApiException e) {
            ErrorExit.exceptionMessage(e, "Could not retrieve dockstore tool: " + id, API_ERROR);
        }
        return tools;
    }

    private List<JsonElement> stringToJSONList(String JSONstring) {
        if (JSONstring == null) {
            return null;
        } else {
            List<JsonElement> objectList = new ArrayList<>();
            JsonArray objectArray = new JsonParser().parse(JSONstring).getAsJsonArray();
            for (int iterator = 0; iterator < objectArray.size(); iterator++) {
                objectList.add(objectArray.get(iterator));
            }
            return objectList;
        }
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

    private void run(String baseDir, String bucketName, String keyPrefix, boolean isTestMode) throws IOException {
        // use swagger-generated classes to talk to dockstore
        setupClientEnvironment();
        String offset = "0";
        List<Tool> tools = null;
        List<Workflow> workflows = getWorkflows(offset);
        List<JsonElement> workflowVersionTools = null;

        if(isTestMode) {
            tools = getTestTool(BAMSTATS);
        } else {
            tools = getTools();
            while(!workflows.isEmpty()) {
                for (Workflow workflow : workflows) {
                    if (workflow.isIsChecker()) {
                        checkerWorkflowCounter++;
                        continue;
                    }
                    publishedWorkflowsCounter++;
                    for (WorkflowVersion version : workflow.getWorkflowVersions()) {
                        publishedWorkflowVersionsCounter++;
                        String trsID = "#workflow/" + workflow.getFullWorkflowPath();
                        String workflowVersionToolsJSONString = getWorkflowTools((workflow.getId()), version.getId(), trsID);
                        //Sort out the errors first
                        if (workflowVersionToolsJSONString == null) {
                            workflowImages204ResponseCounter++;
                            System.out.println("Empty Get Tools Response: " + workflow.getId() + " " + version.getId());
                            logData("./dockstoreToolsWith204Response.csv", "workflows/" + workflow.getId() + "/tools/" + version.getId() + "&" + trsID + "&" + version.getName());
                            continue;
                        } else if (workflowVersionToolsJSONString.equals("failed to retrieve workflow tools")) {
                            continue;
                        }

                        workflowVersionTools = stringToJSONList(workflowVersionToolsJSONString);
                        if (workflowVersionTools.size() == 0) {
                            workflowImagesEmptyObjectCounter++;
                            System.out.println("Workflow Version With No Tools: workflows/" + workflow.getId() + "/tools/" + version.getId());
                            logData("./dockstoreToolsWithEmptyJSONObject.csv", "workflows/" + workflow.getId() + "/tools/" + version.getId() + "&" + trsID + "&" + version.getName());
                        } else {
                            workflowImagesNonEmptyObjCounter++;
                            getWorkflowDockerHubImagesSize(workflowVersionTools, trsID);
                        }
                    }
                }
                offset = Long.toString(Long.parseLong(offset) + 100);
                workflows = getWorkflows(offset);
            }
        }

        final S3Communicator s3Communicator= new S3Communicator("dockstore", endpoint);
        String reportDir = baseDir + File.separator + "report";
        saveToLocal(baseDir, reportDir, tools, new DockerCommunicator());
        logDockerHubImagesData(dockerHubImagesMap);
        logNonDockerHubImagesData(nonDockerHubImagesMap);
        System.out.println("The total image size of all docker images is: " + (totalToolVersionImageSize + totalWorkflowVersionImageSize));
        System.out.println("The total image size of all workflow images is: " + totalWorkflowVersionImageSize);
        System.out.println("The total image size of all Dockstore tool images is: " + totalToolVersionImageSize);
        System.out.println("The total number of published workflows is: " + publishedWorkflowsCounter);
        System.out.println("The total number of checker workflows is: " + checkerWorkflowCounter);
        System.out.println("The total number of published workflow versions is: " + publishedWorkflowVersionsCounter);
        System.out.println("The total number of workflow versions returning 500 is: " + workflowImages500ResponseCounter);
        System.out.println("The total number of workflow versions returning 204 is: " + workflowImages204ResponseCounter);
        System.out.println("The total number of workflow versions returning an empty JSON object is: " + workflowImagesEmptyObjectCounter);
        System.out.println("The total number of workflow versions returning a non-empty JSON object is: " + workflowImagesNonEmptyObjCounter);
        System.out.println("The total number of DockerHub images referenced is: " + dockerHubImageRefCounter);
        System.out.println("The total number of non-DockerHub images referenced is: " + nonDockerHubImageRefCounter);
        System.out.println("The total number of Dockstore tools is: " + toolCounter);
        System.out.println("The total number of Dockstore tool versions is: " + toolVersionCounter);
        System.out.println("The total number of Dockstore tools with DockerHub images is: " + parsedToolCounter);
        System.out.println("The total number of non duplicate Dockstore tools with DockerHub images is: " + parsedNonDupeToolCounter);
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

    private void getWorkflowDockerHubImagesSize(List<JsonElement> workflowVersionTools, String trsID) {
        for (JsonElement workflowTool : workflowVersionTools) {
            String dockerImage = workflowTool.getAsJsonObject().get("docker").getAsString();
            String imageRepoLink = workflowTool.getAsJsonObject().get("link").getAsString();

            // Skip to next iteration if image is non-DockerHub or if image is duplicate
            if (isNonDockerHubImage(dockerImage, imageRepoLink, trsID) || isDuplicateDockerHubImage(dockerImage, trsID)) {
                continue;
            }

            String imagePathSelector = "/repositories/";
            if (imageRepoLink.contains("https://hub.docker.com/_/")) {
                imagePathSelector = "/repositories/library/";
            }

            String[] dockerHubImageURLSegments;
            if (dockerImage.contains("@sha256")) {
                dockerHubImageURLSegments = dockerImage.split("@");
            } else {
                dockerHubImageURLSegments = dockerImage.split(":");
            }

            if (dockerHubImageURLSegments.length > 1) {
                System.out.println("Attempting to get image size: " + dockerImage);
                dockerHubImageRefCounter++;
                dockerHubImagesMap.put(dockerImage, new DockerHubImageDetail(
                    imagePathSelector,
                    dockerHubImageURLSegments[0],
                    dockerHubImageURLSegments[1],
                    trsID,
                    new ArrayList<>()));
                setDockerHubImageData(dockerHubImagesMap.get(dockerImage), "Workflow");
            } else if (dockerImage.split("/").length > 1) {
                System.out.println("Image with no tags: " + dockerImage);
                logData("./dockstoreImageWithNoTags.csv", trsID + "&" + dockerHubImageURLSegments[0]);
                dockerHubImagesMap.put(dockerImage, null);
            } else {
                System.out.println("Image possibly with an invalid docker parameter");
                logData("./possibleInvalidDockerParameter.csv", trsID + "&" + dockerHubImageURLSegments[0]);
                dockerHubImagesMap.put(dockerImage, null);
            }
        }
    }

    private void getToolDockerHubImageSize(ToolVersion version) {
        String versionId = version.getId();
        String versionName = version.getName();
        String imagePathSelector = "/repositories/";
        String trsID = version.getUrl().replace("http://localhost:8080/api/ga4gh/v2/tools/", "");
        String dockerHubImage = versionId.split("registry.hub.docker.com/")[1];

        parsedToolCounter++;
        if (dockerHubImagesMap.containsKey(dockerHubImage)) {
            dockerHubImagesMap.get(dockerHubImage).appendToRepetitionList(trsID);
            System.out.println("Repeated Image Or Non DockerHub Image: " + dockerHubImage);
            return;
        }
        parsedNonDupeToolCounter++;

        String[] dockerHubImageURLSegments = versionId.split("/");
        String imagePath = dockerHubImageURLSegments[1] + "/" + dockerHubImageURLSegments[2].split(":")[0];
        dockerHubImagesMap.put(dockerHubImage, new DockerHubImageDetail(imagePathSelector, imagePath, versionName, trsID, new ArrayList<>()));
        setDockerHubImageData(dockerHubImagesMap.get(dockerHubImage), "Tool");
    }

    private void setDockerHubImageData(DockerHubImageDetail image, String source) {
        String dockerHubImageSize = getDockerHubImageSize(image);
        if(dockerHubImageSize.equals("")) {
            image.setData(image.getImagePath() + "&" + image.getImageTag() + "&" + image.getSelfTrsID(), true);
        } else {
            if (source.equals("Workflow")) {
                totalWorkflowVersionImageSize += Long.parseLong(dockerHubImageSize);
            } else {
                totalToolVersionImageSize += Long.parseLong(dockerHubImageSize);
            }
            image.setImageSize(Long.parseLong(dockerHubImageSize));
            image.setData(image.getImagePath() + "&" + image.getImageTag() + "&" + image.getSelfTrsID() + "&" + image.getImageSize(), false);
        }
    }

    private String getDockerHubImageSize(DockerHubImageDetail image) {
        try {
            ProcessBuilder curlProcessBuilder = new ProcessBuilder();
            ProcessBuilder bashProcessBuilder = new ProcessBuilder();

            String apiRequest = "https://hub.docker.com/v2" + image.getImagePathSelector() + image.getImagePath() + "/tags/" + image.getImageTag() + "/?page_size=100";
            curlProcessBuilder.command("curl", "-s", "GET", apiRequest);
            Process curlProcess = curlProcessBuilder.start();
            String results = IOUtils.toString(curlProcess.getInputStream(), "UTF-8").replace("\n", "").replace("\r", "");
            bashProcessBuilder.command("/bin/sh", "-c", "echo '" + results + "' | jq '.full_size'");
            Process bashProcess = bashProcessBuilder.start();

            // Retrieve the total_size, and if total_size is 0, then add up all of the sizes from the images parameter
            String imageSize = IOUtils.toString(bashProcess.getInputStream(), "UTF-8").trim();
            if (Long.parseLong(imageSize) == 0) {
                bashProcessBuilder.command("/bin/sh", "-c", "echo '" + results + "' | jq '[.images[].size] | add'");
                bashProcess = bashProcessBuilder.start();
                imageSize = IOUtils.toString(bashProcess.getInputStream(), "UTF-8").trim();
            }

            curlProcess.destroy();
            bashProcess.destroy();
            return imageSize;
        } catch (Exception e) {
            System.out.println(e);
            return "";
        }
    }

    private void logDockerHubImagesData(Map<String, DockerHubImageDetail> imageMap) {
        String outputFilePath;
        for (DockerHubImageDetail image: imageMap.values()) {
            if (image == null) {
                continue;
            }
            if (image.getRetrieveStatus()) {
                outputFilePath = "./imagesFailedToRetrieveFromDockerHub.csv";
            } else {
                outputFilePath = "./imagesRetrievedFromDockerHub.csv";
            }
            System.out.println("Writing data: " + image.getDataToWrite() + "&" + image.getTrsIdsRepeatingImage());
            logData(outputFilePath, image.getDataToWrite() + "&" + image.getTrsIdsRepeatingImage().toString()
                    .replace("[", "")
                    .replace("]", "")
                    .trim()
            );
        }
    }

    private void logNonDockerHubImagesData(Map<String, NonDockerHubImageDetail> imageMap) {
        for (NonDockerHubImageDetail image: imageMap.values()) {
            String dataToWrite = image.getSelfTrsID() + "&" + image.getDockerImage() + "&" + image.getImageRepoLink() + "&"
                    + image.getTrsIDsRepeatingImage().toString().replace("[", "").replace("]", "").trim();
            logData("./nonDockerHubImage.csv", dataToWrite);
        }
    }

    private void logData(String filepath, String data) {
        try {
            File file = new File(filepath);
            Writer w = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8");
            PrintWriter pw = new PrintWriter(w);
            pw.println(data);
            pw.flush();
            pw.close();
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }

    private void update(ToolVersion version, String dirPath, List<VersionDetail> versionsDetails, String img, DockerCommunicator dockerCommunicator) throws IOException {
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

            dockerCommunicator.removeDockerImage(img);

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

    public void saveToLocal(String baseDir, String reportDir, final List<Tool> tools, DockerCommunicator dockerCommunicator) throws IOException {
        toolsToVersions = ReportGenerator.loadJSONMap(reportDir);

        for(Tool tool : tools)  {
            if (tool.getId().contains("#workflow")) {
                continue;
            }
            toolCounter++;
            String toolName = tool.getToolname();
            String dirPath = baseDir + File.separator + tool.getId();
            //DirectoryGenerator.createDir(dirPath);
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
                toolVersionCounter++;
                String img = version.getImage();
                if (!version.getId().contains("registry.hub.docker")) {
                    nonDockerHubImageRefCounter++;
                    continue;
                }
                dockerHubImageRefCounter++;
                getToolDockerHubImageSize(version);
//                update(version, dirPath, versionsDetails, img, dockerCommunicator);
            }
//            toolsToVersions.put(toolName, versionsDetails);
        }
        dockerCommunicator.closeDocker();
        out.println("Closed docker client");
    }

    //-----------------------Set up to connect to GA4GH API-----------------------
    protected void setupClientEnvironment() {
        String userHome = System.getProperty("user.home");

        String configFilePath = userHome + File.separator + ".toolbackup" + File.separator + "config.ini";

        try {
            File configFile = new File(configFilePath);
            this.config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }

        // pull out the variables from the config
        String token = config.getString("token", "");
        String serverUrl = config.getString("server-url", "http://localhost:4200/api");
        System.out.println(serverUrl);

        try {
            endpoint = config.getString("endpoint");
        } catch(NullPointerException e) {
            throw new RuntimeException("Expected " + configFilePath + " with an endpoint initialization");
        }

        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();
        defaultApiClient.addDefaultHeader("Authorization", "Bearer " + token);
        defaultApiClient.setBasePath(serverUrl);

        this.containersApi = new ContainersApi(defaultApiClient);
        this.workflowsApi = new WorkflowsApi(defaultApiClient);
        this.usersApi = new UsersApi(defaultApiClient);
        this.ga4ghApi = new Ga4GhApi(defaultApiClient);

        try {
            if (this.usersApi.getApiClient() != null) {
                this.isAdmin = this.usersApi.getUser().isIsAdmin();
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
