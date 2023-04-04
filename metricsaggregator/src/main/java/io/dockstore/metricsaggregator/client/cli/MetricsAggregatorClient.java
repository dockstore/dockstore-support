/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.metricsaggregator.client.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import io.dockstore.metricsaggregator.MetricsAggregatorConfig;
import io.dockstore.metricsaggregator.MetricsAggregatorS3Client;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.AggregateMetricsCommand;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.SubmitValidationData;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.Configuration;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationExecution.ValidatorToolEnum;
import io.dockstore.webservice.core.Partner;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsAggregatorClient {

    public static final String CONFIG_FILE_NAME = "metrics-aggregator.config";
    private static final Logger LOG = LoggerFactory.getLogger(MetricsAggregatorClient.class);
    public static final int SUCCESS_EXIT_CODE = 0;
    public static final int FAILURE_EXIT_CODE = 1;
    public static final String CONFIG_FILE_ERROR = "Could not get configuration file";

    public MetricsAggregatorClient() {

    }

    public static void main(String[] args) {
        MetricsAggregatorClient metricsAggregatorClient = new MetricsAggregatorClient();
        final CommandLineArgs commandLineArgs = new CommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final AggregateMetricsCommand aggregateMetricsCommand = new AggregateMetricsCommand();
        final SubmitValidationData submitValidationData = new SubmitValidationData();
        jCommander.addCommand(aggregateMetricsCommand);
        jCommander.addCommand(submitValidationData);

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
                    metricsAggregatorClient.aggregateMetrics(metricsAggregatorConfig);
                } catch (Exception e) {
                    LOG.error("Could not aggregate metrics", e);
                    System.exit(FAILURE_EXIT_CODE);
                }
            }
        } else if ("submit-validation-data".equals(jCommander.getParsedCommand())) {
            if (submitValidationData.isHelp()) {
                jCommander.usage();
            } else {
                final Optional<INIConfiguration> config = getConfiguration(submitValidationData.getConfig());
                if (config.isEmpty()) {
                    System.exit(FAILURE_EXIT_CODE);
                }

                try {
                    final MetricsAggregatorConfig metricsAggregatorConfig = new MetricsAggregatorConfig(config.get());
                    metricsAggregatorClient.submitValidationData(metricsAggregatorConfig, submitValidationData.getValidator(),
                            submitValidationData.isSuccessful(), submitValidationData.getDataFilePath(), submitValidationData.getPlatform());
                } catch (Exception e) {
                    LOG.error("Could not submit validation metrics to Dockstore", e);
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


    private void submitValidationData(MetricsAggregatorConfig config, ValidatorToolEnum validator, boolean isSuccessful, String dataFilePath, Partner platform) throws IOException {
        ApiClient apiClient = setupApiClient(config.getDockstoreServerUrl(), config.getDockstoreToken());
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);

        final File dataFile = new File(dataFilePath);
        List<String> validatedWorkflows = Files.readAllLines(dataFile.toPath()).stream()
                .filter(StringUtils::isNotBlank) // Remove whitespace lines
                .map(String::trim) // Remove whitespace from workflow names
                .toList();

        // Workflows that are listed in the data file are workflows that were validated using the validator. They all have the same result, indicated by isSuccessful
        List<ValidationExecution> validationExecutions = List.of(new ValidationExecution().validatorTool(validator).isValid(isSuccessful).dateExecuted(
                Instant.now().toString()));
        ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody().validationExecutions(validationExecutions);

        for (String validatedWorkflow : validatedWorkflows) {
            // The workflow name is specified as the TRS ID and version name are separated by ':', like #workflow/github.com/broadinstitute/gatk-sv/MakeBincovMatrix:master
            String[] workflowNameComponents = validatedWorkflow.split(":");
            if (workflowNameComponents.length < 2) {
                LOG.error("Workflow {} does not contain the TRS ID and version name, skipping", validatedWorkflow);
                continue;
            }

            String trsId = workflowNameComponents[0];
            String versionName = workflowNameComponents[1];
            try {
                extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform.toString(), trsId, versionName,
                        "Validation executions submitted using dockstore-support metricsaggregator");
                System.out.printf("Submitted validation metrics for tool ID %s, version %s, %s validated by %s on platform %s%n", trsId, versionName, isSuccessful ? "successfully" : "unsuccessfully", validator, platform);
            } catch (Exception e) {
                // Could end up here if the workflow no longer exists. Log then continue processing
                LOG.error("Could not submit validation executions to Dockstore for workflow {}", validatedWorkflow, e);
            }
        }
    }
}
