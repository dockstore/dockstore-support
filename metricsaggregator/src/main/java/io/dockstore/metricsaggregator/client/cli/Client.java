package io.dockstore.metricsaggregator.client.cli;

import static io.dockstore.cliutilities.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.cliutilities.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.cliutilities.ExceptionHandler.errorMessage;
import static io.dockstore.cliutilities.ExceptionHandler.exceptionMessage;
import static io.dockstore.cliutilities.JCommanderUtility.out;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import io.dockstore.metricsaggregator.MetricsAggregatorConfig;
import io.dockstore.metricsaggregator.MetricsS3Client;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.AggregateMetricsCommand;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.Configuration;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import java.io.File;
import java.net.URISyntaxException;
import java.util.Optional;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {

    public static final String CONFIG_FILE_NAME = "metrics-aggregator.config";
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    public Client() {

    }

    public static void main(String[] args) {
        Client client = new Client();
        final CommandLineArgs commandLineArgs = new CommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final AggregateMetricsCommand aggregateMetricsCommand = new AggregateMetricsCommand();
        jCommander.addCommand(aggregateMetricsCommand);

        try {
            jCommander.parse(args);
        } catch (MissingCommandException e) {
            jCommander.usage();
            if (e.getUnknownCommand().isEmpty()) {
                out("No command entered");
                return;
            } else {
                exceptionMessage(e, "Unknown command", COMMAND_ERROR);
            }
        }

        if (commandLineArgs.isHelp()) {
            jCommander.usage();
        } else if ("aggregate-metrics".equals(jCommander.getParsedCommand())) {
            final Optional<INIConfiguration> config = getConfiguration(aggregateMetricsCommand.getConfig());
            if (config.isEmpty()) {
                errorMessage("Error reading configuration file", GENERIC_ERROR);
            }

            try {
                final MetricsAggregatorConfig metricsAggregatorConfig = new MetricsAggregatorConfig(config.get());
                client.aggregateMetrics(metricsAggregatorConfig, aggregateMetricsCommand.getToolId(), aggregateMetricsCommand.getVersionId());
            } catch (Exception e) {
                exceptionMessage(e, "Could not aggregate metrics", GENERIC_ERROR);
            }
        }
    }

    public static Optional<INIConfiguration> getConfiguration(File iniFile) {
        Configurations configs = new Configurations();

        try {
            INIConfiguration config = configs.ini(iniFile);
            return Optional.of(config);
        } catch (ConfigurationException e) {
            return Optional.empty();
        }
    }

    private ApiClient setupApiClient(String serverUrl, String token) {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(serverUrl);
        apiClient.addDefaultHeader("Authorization", "Bearer " + token);
        return apiClient;
    }

    private void aggregateMetrics(MetricsAggregatorConfig config, String toolId, String versionId) throws URISyntaxException {
        ApiClient apiClient = setupApiClient(config.getDockstoreServerUrl(), config.getDockstoreToken());
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);

        MetricsS3Client metricsS3Client;
        if (config.getS3EndpointOverride() == null) {
            metricsS3Client = new MetricsS3Client(config.getS3Bucket());
        } else {
            metricsS3Client = new MetricsS3Client(config.getS3Bucket(), config.getS3EndpointOverride());
        }

        metricsS3Client.aggregateMetrics(extendedGa4GhApi);
    }
}
