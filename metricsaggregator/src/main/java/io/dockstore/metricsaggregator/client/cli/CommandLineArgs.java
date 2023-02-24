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

        public File getConfig() {
            return config;
        }
    }
}
