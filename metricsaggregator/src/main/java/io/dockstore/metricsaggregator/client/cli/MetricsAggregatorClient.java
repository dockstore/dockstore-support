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

import static io.dockstore.utils.CLIConstants.FAILURE_EXIT_CODE;
import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import io.dockstore.common.Partner;
import io.dockstore.metricsaggregator.MetricsAggregatorConfig;
import io.dockstore.metricsaggregator.MetricsAggregatorS3Client;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.AggregateMetricsCommand;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.SubmitTerraMetrics;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.SubmitValidationData;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationExecution.ValidatorToolEnum;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.commons.configuration2.INIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsAggregatorClient {

    public static final String CONFIG_FILE_NAME = "metrics-aggregator.config";
    // Constants for the data file's CSV fields used by submit-validation-data
    public static final int TRS_ID_INDEX = 0;
    public static final int VERSION_NAME_INDEX = 1;
    public static final int IS_VALID_INDEX = 2;
    public static final int DATE_EXECUTED_INDEX = 3;
    public static final List<String> VALIDATION_FILE_CSV_FIELDS = List.of("trsId", "versionName", "isValid", "dateExecuted");

    private static final Logger LOG = LoggerFactory.getLogger(MetricsAggregatorClient.class);

    public MetricsAggregatorClient() {

    }

    public static void main(String[] args) {
        final Instant startTime = Instant.now();
        MetricsAggregatorClient metricsAggregatorClient = new MetricsAggregatorClient();
        final CommandLineArgs commandLineArgs = new CommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final AggregateMetricsCommand aggregateMetricsCommand = new AggregateMetricsCommand();
        final SubmitValidationData submitValidationData = new SubmitValidationData();
        final SubmitTerraMetrics submitTerraMetrics = new SubmitTerraMetrics();

        jCommander.addCommand(aggregateMetricsCommand);
        jCommander.addCommand(submitValidationData);
        jCommander.addCommand(submitTerraMetrics);

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

        if (jCommander.getParsedCommand() == null || commandLineArgs.isHelp()) {
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
                            submitValidationData.getValidatorVersion(), submitValidationData.getDataFilePath(),
                            submitValidationData.getPlatform());
                } catch (Exception e) {
                    LOG.error("Could not submit validation metrics to Dockstore", e);
                    System.exit(FAILURE_EXIT_CODE);
                }
            }
        } else if ("submit-terra-metrics".equals(jCommander.getParsedCommand())) {
            if (submitTerraMetrics.isHelp()) {
                jCommander.usage();
            } else {
                final Optional<INIConfiguration> config = getConfiguration(submitTerraMetrics.getConfig());
                if (config.isEmpty()) {
                    System.exit(FAILURE_EXIT_CODE);
                }

                try {
                    final MetricsAggregatorConfig metricsAggregatorConfig = new MetricsAggregatorConfig(config.get());
                    final TerraMetricsSubmitter submitTerraMetricsCommand = new TerraMetricsSubmitter(metricsAggregatorConfig,
                            submitTerraMetrics);
                    submitTerraMetricsCommand.submitMetrics();
                } catch (Exception e) {
                    LOG.error("Could not submit terra metrics to Dockstore", e);
                    System.exit(FAILURE_EXIT_CODE);
                }
            }
        }
        final Instant endTime = Instant.now();
        LOG.info("{} took {}", jCommander.getParsedCommand(), Duration.between(startTime, endTime));
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

    private void submitValidationData(MetricsAggregatorConfig config, ValidatorToolEnum validator, String validatorVersion,
            String dataFilePath, Partner platform) throws IOException {
        ApiClient apiClient = setupApiClient(config.getDockstoreServerUrl(), config.getDockstoreToken());
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);

        final File dataFile = new File(dataFilePath);
        List<String> csvLines = Files.readAllLines(dataFile.toPath());
        // Remove first line containing CSV fields
        if (csvLines.get(0).contains(String.join(",", VALIDATION_FILE_CSV_FIELDS))) {
            csvLines.remove(0);
        }

        for (String csvLine : csvLines) {
            String[] lineComponents = csvLine.split(",");
            if (lineComponents.length < VALIDATION_FILE_CSV_FIELDS.size()) {
                LOG.error("Line '{}' does not contain all the required fields, skipping", csvLine);
                continue;
            }

            String trsId = lineComponents[TRS_ID_INDEX];
            String versionName = lineComponents[VERSION_NAME_INDEX];

            // Parse boolean value for isValid column
            String isValidValue = lineComponents[IS_VALID_INDEX];
            boolean isValid;
            if ("true".equalsIgnoreCase(isValidValue) || "false".equalsIgnoreCase(isValidValue)) {
                isValid = Boolean.parseBoolean(isValidValue);
            } else {
                LOG.error("isValid column value '{}' is not a boolean value, skipping line '{}'", isValidValue, csvLine);
                continue;
            }
            String dateExecuted = lineComponents[DATE_EXECUTED_INDEX];
            ValidationExecution validationExecution = new ValidationExecution().validatorTool(validator)
                    .validatorToolVersion(validatorVersion).isValid(isValid);
            validationExecution.setDateExecuted(dateExecuted);
            ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody().validationExecutions(List.of(validationExecution));

            try {
                extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform.toString(), trsId, versionName,
                        "Validation executions submitted using dockstore-support metricsaggregator");
                System.out.printf("Submitted validation metrics for tool ID %s, version %s, %s validated by %s %s on platform %s%n", trsId,
                        versionName, isValid ? "successfully" : "unsuccessfully", validator, validatorVersion, platform);
            } catch (Exception e) {
                // Could end up here if the workflow no longer exists. Log then continue processing
                LOG.error("Could not submit validation executions to Dockstore for workflow {}", csvLine, e);
            }
        }
    }
}
