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
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolFile;
import io.dockstore.openapi.client.model.ToolVersion;
import java.io.File;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.slf4j.LoggerFactory;

public class Client {

    public static final int GENERIC_ERROR = 1;          // General error, not yet described by an error type
    public static final int CONNECTION_ERROR = 150;     // Connection exception
    public static final int IO_ERROR = 3;               // IO throws an exception
    public static final int API_ERROR = 6;              // API throws an exception
    public static final int CLIENT_ERROR = 4;           // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10;         // Command is not successful, but not due to errors
    private static final Logger ROOT_LOGGER = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    // retrieves only the bamstats tool as shown in https://dockstore.org/docs/getting-started-with-docker
    private static final String BAMSTATS = "quay.io/briandoconnor/dockstore-tool-bamstats";

    private static final LocalDateTime TIME_NOW = LocalDateTime.now();
    private static final String STRING_TIME;
    private final OptionSet options;
    private ContainersApi containersApi;
    private WorkflowsApi workflowsApi;
    private UsersApi usersApi;
    private Ga4Ghv20Api ga4ghApi;
    private boolean isAdmin = false;
    private String endpoint;

    private Map<String, List<VersionDetail>> toolsToVersions;
    private HierarchicalINIConfiguration config;

    public Client(OptionSet options) {
        this.options = options;
    }

    static {
        ROOT_LOGGER.setLevel(Level.WARN);
        STRING_TIME = FormattedTimeGenerator.getFormattedTimeNow(TIME_NOW);
    }

    public static void main(String[] argv) {
        out.println("Back-up script started: " + STRING_TIME);

        out.println(Arrays.toString(argv));

        OptionParser parser = new OptionParser();
        final ArgumentAcceptingOptionSpec<String> localDir = parser.accepts("local-dir", "local directory to which files will be backed-up").withRequiredArg().defaultsTo(".").ofType(String.class);
        final ArgumentAcceptingOptionSpec<Boolean> isTestMode = parser.accepts("test-mode-activate", "if true test mode is activated").withRequiredArg().ofType(Boolean.class);

        final OptionSet options = parser.parse(argv);
        Client client = new Client(options);

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
        List<Tool> tools = null;
        try {
            tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            ErrorExit.exceptionMessage(e, "Could not retrieve dockstore tools", API_ERROR);
        }
        return tools;
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

    private void run(String baseDir, boolean isTestMode) throws UnknownHostException {
        // use swagger-generated classes to talk to dockstore
        setupClientEnvironment();
        List<Tool> tools = null;
        if (isTestMode) {
            tools = getTestTool(BAMSTATS);
        } else {
            tools = getTools();
        }

        String reportDir = baseDir + File.separator + "report";
    }


    //-----------------------Report-----------------------
    public long getFilesTotalSizeB(List<File> forUpload) {
        long totalSize = 0;
        for (File file : forUpload) {
            totalSize += file.length();
        }
        return totalSize;
    }



    //-----------------------Save to local-----------------------

    private VersionDetail findLocalVD(List<VersionDetail> versionsDetails, String version) {
        for (VersionDetail row : versionsDetails) {
            if (row.getVersion().equals(version) && !Objects.equals(row.getPath(), "")) {
                return row;
            }
        }
        return null;
    }

    private VersionDetail findInvalidVD(List<VersionDetail> versionsDetails, String version) {
        for (VersionDetail row : versionsDetails) {
            if (row.getVersion().equals(version) && !row.isValid()) {
                return row;
            }
        }
        return null;
    }

    private void update(ToolVersion version, String dirPath, List<VersionDetail> versionsDetails, String img) {
    }

    public void saveToLocal(String baseDir, String reportDir, final List<Tool> tools) {
        toolsToVersions = ReportGenerator.loadJSONMap(reportDir);

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
                // TODO save files here
                String id = version.getId();
                List<ToolFile> zip = ga4ghApi.toolsIdVersionsVersionIdTypeFilesGet(tool.getId(), null, version.getId(), "zip");
            }
            toolsToVersions.put(toolName, versionsDetails);
        }
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
        String serverUrl = config.getString("server-url", "https://www.dockstore.org:443/api");

        try {
            endpoint = config.getString("endpoint");
        } catch (NullPointerException e) {
            throw new RuntimeException("Expected " + configFilePath + " with an endpoint initialization");
        }

        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();
        defaultApiClient.addDefaultHeader("Authorization", "Bearer " + token);
        defaultApiClient.setBasePath(serverUrl);

        this.containersApi = new ContainersApi(defaultApiClient);
        this.workflowsApi = new WorkflowsApi(defaultApiClient);
        this.ga4ghApi = new Ga4Ghv20Api(defaultApiClient);
        this.usersApi = new UsersApi(defaultApiClient);

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
