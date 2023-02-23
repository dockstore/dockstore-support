package io.dockstore.metricsaggregator.client.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.io.File;

public class CommandLineArgs {
    @Parameter(names = "--help", description = "Prints help for metricsaggregator", help = true)
    private boolean help = false;

    public boolean isHelp() {
        return help;
    }

    @Parameters(commandNames = { "aggregate-metrics" }, commandDescription = "Aggregate metrics in S3")
    public static class AggregateMetricsCommand extends CommandLineArgs {
        @Parameter(names = {"-c", "--config"}, description = "The config file path.")
        private File config = new File("./" + Client.CONFIG_FILE_NAME);

        @Parameter(names = {"-t", "--toolId"}, description = "Aggregate metrics for the specific tool ID.")
        private String toolId;

        @Parameter(names = {"-v", "--versionId"}, description = "Aggregate metrics for the specific version ID. Must also provide the tool ID.")
        private String versionId;

        public File getConfig() {
            return config;
        }

        public String getToolId() {
            return toolId;
        }

        public String getVersionId() {
            return versionId;
        }
    }
}
