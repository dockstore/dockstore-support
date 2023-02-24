package io.dockstore.metricsaggregator.client.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import io.dockstore.metricsaggregator.MetricsAggregatorConfig;
import io.dockstore.metricsaggregator.MetricsAggregatorS3Client;
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
    public static final int SUCCESS_EXIT_CODE = 0;
    public static final int FAILURE_EXIT_CODE = 1;
    public static final String CONFIG_FILE_ERROR = "Could not get configuration file";

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
                LOG.error("No command entered", e);
            } else {
                LOG.error("Unknown command", e);
            }
            System.exit(FAILURE_EXIT_CODE);
        } catch (ParameterException e) {
            jCommander.usage();
            LOG.error("Error parsing arguments", e);
            System.exit(FAILURE_EXIT_CODE);
        }

        if (commandLineArgs.isHelp()) {
            jCommander.usage();
        } else if ("aggregate-metrics".equals(jCommander.getParsedCommand())) {
            if (aggregateMetricsCommand.isHelp()) {
                jCommander.usage();
            } else {
                final Optional<INIConfiguration> config = getConfiguration(aggregateMetricsCommand.getConfig());
                if (config.isEmpty()) {
                    System.exit(FAILURE_EXIT_CODE);
                }

                try {
                    final MetricsAggregatorConfig metricsAggregatorConfig = new MetricsAggregatorConfig(config.get());
                    client.aggregateMetrics(metricsAggregatorConfig);
                } catch (Exception e) {
                    LOG.error("Could not aggregate metrics", e);
                    System.exit(FAILURE_EXIT_CODE);
                }
            }
        }
    }

    public static Optional<INIConfiguration> getConfiguration(File iniFile) {
        Configurations configs = new Configurations();

        try {
            INIConfiguration config = configs.ini(iniFile);
            return Optional.of(config);
        } catch (ConfigurationException e) {
            LOG.error(CONFIG_FILE_ERROR, e);
            return Optional.empty();
        }
    }

    private ApiClient setupApiClient(String serverUrl, String token) {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(serverUrl);
        apiClient.addDefaultHeader("Authorization", "Bearer " + token);
        return apiClient;
    }

    private void aggregateMetrics(MetricsAggregatorConfig config) throws URISyntaxException {
        ApiClient apiClient = setupApiClient(config.getDockstoreServerUrl(), config.getDockstoreToken());
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);

        MetricsAggregatorS3Client metricsAggregatorS3Client;
        if (config.getS3EndpointOverride() == null) {
            metricsAggregatorS3Client = new MetricsAggregatorS3Client(config.getS3Bucket());
        } else {
            metricsAggregatorS3Client = new MetricsAggregatorS3Client(config.getS3Bucket(), config.getS3EndpointOverride());
        }

        metricsAggregatorS3Client.aggregateMetrics(extendedGa4GhApi);
    }
}
