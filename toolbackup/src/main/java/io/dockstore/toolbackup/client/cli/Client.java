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

import static java.lang.System.out;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.Configuration;
import io.dockstore.openapi.client.Pair;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolVersion;
import jakarta.ws.rs.core.GenericType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.HttpStatus;
import org.slf4j.LoggerFactory;

public class Client {

    public static final int CONNECTION_ERROR = 150;     // Connection exception
    public static final int IO_ERROR = 3;               // IO throws an exception
    public static final int API_ERROR = 6;              // API throws an exception
    public static final int CLIENT_ERROR = 4;           // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10;         // Command is not successful, but not due to errors

    public static final int LIMIT = 100;

    private static final Logger ROOT_LOGGER = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    // retrieves only https://dockstore.org/workflows/github.com/gatk-workflows/gatk4-data-processing/processing-for-variant-discovery-gatk4:2.1.0?tab=info
    private static final String TEST_WORKFLOW = "#workflow/github.com/gatk-workflows/gatk4-data-processing/processing-for-variant-discovery-gatk4";

    private static final LocalDateTime TIME_NOW = LocalDateTime.now();
    private static final String STRING_TIME;
    private ContainersApi containersApi;
    private WorkflowsApi workflowsApi;
    private Ga4Ghv20Api ga4ghApi;

    private HierarchicalINIConfiguration config;

    public Client() {
    }

    static {
        ROOT_LOGGER.setLevel(Level.WARN);
        STRING_TIME = FormattedTimeGenerator.getFormattedTimeNow(TIME_NOW);
    }

    public static void main(String[] argv) {
        out.println("Back-up script started: " + STRING_TIME);

        out.println(Arrays.toString(argv));

        OptionParser parser = new OptionParser();
        final ArgumentAcceptingOptionSpec<String> localDir = parser.accepts("local-dir", "local directory to which files will be backed-up").withRequiredArg().defaultsTo("backup_storage").ofType(String.class);
        final ArgumentAcceptingOptionSpec<Boolean> isTestMode = parser.accepts("test-mode-activate", "if true test mode is activated").withRequiredArg().ofType(Boolean.class).defaultsTo(true);

        final OptionSet options = parser.parse(argv);
        Client client = new Client();

        String local = options.valueOf(localDir);
        String dirPath = Paths.get(local).toAbsolutePath().toString();
        DirectoryGenerator.createDir(dirPath);

        try {
            client.run(dirPath, options.valueOf(isTestMode));
        } catch (UnknownHostException e) {
            ErrorExit.exceptionMessage(e, "No internet access", CONNECTION_ERROR);
        }

        final LocalDateTime end = LocalDateTime.now();
        out.println("Back-up script completed successfully in " + FormattedTimeGenerator.elapsedTime(TIME_NOW, end));
    }

    //-----------------------Main invocations-----------------------
    private List<Tool> getTools() {
        List<Tool> tools = new ArrayList<>();
        try {
            /// need to iterate until there are no more tools
            List<Tool> evenMoreTools;
            for (int i = 0; ; i = i + 1) {
                evenMoreTools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null, null, null, String.valueOf(i), LIMIT);
                if (evenMoreTools.isEmpty()) {
                    break;
                }
                tools.addAll(evenMoreTools);
            }
        } catch (ApiException e) {
            ErrorExit.exceptionMessage(e, "Could not retrieve dockstore tools", API_ERROR);
        }
        return tools;
    }

    private List<Tool> getTestTool() {
        List<Tool> tools = new ArrayList<>();
        try {
            tools.add(ga4ghApi.toolsIdGet(Client.TEST_WORKFLOW));
        } catch (ApiException e) {
            ErrorExit.exceptionMessage(e, "Could not retrieve dockstore tool: " + Client.TEST_WORKFLOW, API_ERROR);
        }
        return tools;
    }

    private void run(String baseDir, boolean isTestMode) throws UnknownHostException {
        // use swagger-generated classes to talk to dockstore
        setupClientEnvironment();
        List<Tool> tools = null;
        if (isTestMode) {
            tools = getTestTool();
        } else {
            tools = getTools();
        }

        String reportDir = baseDir + File.separator + "report";
        this.saveToLocal(baseDir, reportDir, tools);
    }


    //-----------------------Report-----------------------
    public long getFilesTotalSizeB(List<File> forUpload) {
        long totalSize = 0;
        for (File file : forUpload) {
            totalSize += file.length();
        }
        return totalSize;
    }

    public void saveToLocal(String baseDir, String reportDir, final List<Tool> tools) {
        Map<String, List<VersionDetail>> toolsToVersions = ReportGenerator.loadJSONMap(reportDir);

        for (Tool tool : tools) {
            String toolName = tool.getName();
            String dirPath = baseDir + File.separator + tool.getId();
            DirectoryGenerator.createDir(dirPath);
            if (toolName == null) {
                continue;
            }

            List<VersionDetail> versionsDetails;
            if (toolsToVersions.containsKey(toolName)) {
                versionsDetails = toolsToVersions.get(toolName);
            } else {
                versionsDetails = new ArrayList<>();
            }

            List<ToolVersion> versions = tool.getVersions();
            for (ToolVersion version : versions) {
                String dirVersionPath = dirPath + File.separator + version.getName();
                // assume that each workflow only has one language type, which is probably ok for now

                // next line works, but we want the actual bytes
                // List<ToolFile> zip = ga4ghApi.toolsIdVersionsVersionIdTypeFilesGet(tool.getId(), version.getDescriptorType().get(0).toString(), version.getName(), null);
                File zipFile = null;
                boolean returnSuccess = false;
                do {
                    try {
                        zipFile = invokeApiForZipDownload(
                            "/ga4gh/trs/v2/tools/" + URLEncoder.encode(tool.getId(), StandardCharsets.UTF_8) + "/versions/" + URLEncoder.encode(version.getName(), StandardCharsets.UTF_8) + "/"
                                + version.getDescriptorType().get(0).toString() + "/files",
                            new GenericType<>() {
                            }, workflowsApi.getApiClient());
                        returnSuccess = true;
                    } catch (ApiException e) {
                        // probably better way to do this through httpclient
                        if (e.getCode() == HttpStatus.SC_FORBIDDEN) {
                            // we've been rate-limited, back-off and try again, should use exponential back-off
                            try {
                                Thread.sleep(Duration.ofMinutes(1).toMillis());
                                System.out.println("Retry " + version.getId());
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }

                    }
                } while (!returnSuccess);

                if (zipFile == null) {
                    continue;
                }
                System.out.println("Processing " + version.getId());
                try {
                    try (ZipFile zip = new ZipFile(zipFile)) {
                        Enumeration<? extends ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            File entryDestination = new File(dirVersionPath,  entry.getName());
                            if (entry.isDirectory()) {
                                entryDestination.mkdirs();
                            } else {
                                entryDestination.getParentFile().mkdirs();
                                try (OutputStream out = new FileOutputStream(entryDestination)) {
                                    zip.getInputStream(entry).transferTo(out);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Filesystem issue with " + version.getId() + " " + e.getMessage());
                }
            }
            toolsToVersions.put(toolName, versionsDetails);
            try {
                Thread.sleep(Duration.ofMinutes(1).toMillis());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static File invokeApiForZipDownload(String path, GenericType<File> type, ApiClient client) {
        try {
            return client
                .invokeAPI(path, "GET", List.of(new Pair("format", "zip")), null, new HashMap<>(), new HashMap<>(), "application/zip", "application/zip",
                    new String[]{"BEARER"}, type);
        } catch (IllegalArgumentException | ApiException e) {
            System.out.println("Issue with: " + path + " " + e.getMessage());
            return null;
        }
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
        String serverUrl = config.getString("server-url", "https://www.dockstore.org:443/api");

        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();
        defaultApiClient.addDefaultHeader("Authorization", "Bearer " + token);
        defaultApiClient.setBasePath(serverUrl);

        this.containersApi = new ContainersApi(defaultApiClient);
        this.workflowsApi = new WorkflowsApi(defaultApiClient);
        this.ga4ghApi = new Ga4Ghv20Api(defaultApiClient);

        defaultApiClient.setDebugging(ErrorExit.DEBUG.get());
    }

    public ContainersApi getContainersApi() {
        return containersApi;
    }
}
