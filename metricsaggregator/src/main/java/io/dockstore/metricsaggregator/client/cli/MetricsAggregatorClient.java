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

import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;
import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import io.dockstore.common.Partner;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorConfig;
import io.dockstore.metricsaggregator.MetricsAggregatorS3Client;
import io.dockstore.metricsaggregator.MetricsAggregatorS3Client.S3DirectoryInfo;
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
import java.util.Collection;
import java.util.List;
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
                LOG.error("No command entered");
            } else {
                LOG.error("Unknown command");
            }
            exceptionMessage(e, "The command is missing", GENERIC_ERROR);
        } catch (ParameterException e) {
            jCommander.usage();
            exceptionMessage(e, "Error parsing arguments", GENERIC_ERROR);
        }

        if (jCommander.getParsedCommand() == null || commandLineArgs.isHelp()) {
            jCommander.usage();
        } else if ("aggregate-metrics".equals(jCommander.getParsedCommand())) {
            if (aggregateMetricsCommand.isHelp()) {
                jCommander.usage();
            } else {
                INIConfiguration config = getConfiguration(aggregateMetricsCommand.getConfig());

                try {
                    final MetricsAggregatorConfig metricsAggregatorConfig = new MetricsAggregatorConfig(config);
                    metricsAggregatorClient.aggregateMetrics(aggregateMetricsCommand, metricsAggregatorConfig);
                } catch (Exception e) {
                    exceptionMessage(e, "Could not aggregate metrics", GENERIC_ERROR);
                }
            }
        } else if ("submit-validation-data".equals(jCommander.getParsedCommand())) {
            if (submitValidationData.isHelp()) {
                jCommander.usage();
            } else {
                INIConfiguration config = getConfiguration(submitValidationData.getConfig());

                try {
                    final MetricsAggregatorConfig metricsAggregatorConfig = new MetricsAggregatorConfig(config);
                    metricsAggregatorClient.submitValidationData(metricsAggregatorConfig, submitValidationData.getValidator(),
                            submitValidationData.getValidatorVersion(), submitValidationData.getDataFilePath(),
                            submitValidationData.getPlatform(),
                            submitValidationData.getExecutionId());
                } catch (Exception e) {
                    exceptionMessage(e, "Could not submit validation metrics to Dockstore", GENERIC_ERROR);
                }
            }
        } else if ("submit-terra-metrics".equals(jCommander.getParsedCommand())) {
            if (submitTerraMetrics.isHelp()) {
                jCommander.usage();
            } else {
                INIConfiguration config = getConfiguration(submitTerraMetrics.getConfig());

                try {
                    final MetricsAggregatorConfig metricsAggregatorConfig = new MetricsAggregatorConfig(config);
                    final TerraMetricsSubmitter submitTerraMetricsCommand = new TerraMetricsSubmitter(metricsAggregatorConfig,
                            submitTerraMetrics);
                    submitTerraMetricsCommand.submitTerraMetrics();
                } catch (Exception e) {
                    exceptionMessage(e, "Could not submit Terra metrics to Dockstore", GENERIC_ERROR);
                }
            }
        }

        if (jCommander.getParsedCommand() != null) {
            final Instant endTime = Instant.now();
            LOG.info("{} took {}", jCommander.getParsedCommand(), Duration.between(startTime, endTime));
        }
    }

    private void aggregateMetrics(AggregateMetricsCommand aggregateMetricsCommand, MetricsAggregatorConfig config) throws URISyntaxException {
        final List<String> trsIdsToAggregate = aggregateMetricsCommand.getTrsIds();
        final boolean skipPostingToDockstore = aggregateMetricsCommand.isSkipDockstore();
        ApiClient apiClient = setupApiClient(config.getDockstoreConfig().serverUrl(), config.getDockstoreConfig().token());
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);

        MetricsAggregatorS3Client metricsAggregatorS3Client;
        if (config.getS3Config().endpointOverride() == null) {
            metricsAggregatorS3Client = new MetricsAggregatorS3Client(config.getS3Config().bucket());
        } else {
            metricsAggregatorS3Client = new MetricsAggregatorS3Client(config.getS3Config().bucket(), config.getS3Config().endpointOverride());
        }
        LOG.info("Aggregating metrics with {}. Submitting metrics to Dockstore is {}", aggregateMetricsCommand.isWithAthena() ? "AWS Athena" : "Java", skipPostingToDockstore ? "skipped" : "enabled");

        final Instant getDirectoriesStartTime = Instant.now();
        List<S3DirectoryInfo> s3DirectoriesToAggregate;
        if (trsIdsToAggregate == null || trsIdsToAggregate.isEmpty()) {
            LOG.info("Aggregating metrics for all entries");
            s3DirectoriesToAggregate = metricsAggregatorS3Client.getDirectories(); // Aggregate all directories
        } else {
            LOG.info("Aggregating metrics for TRS IDs: {}", trsIdsToAggregate);
            s3DirectoriesToAggregate = trsIdsToAggregate.stream()
                    .map(metricsAggregatorS3Client::getDirectoriesForTrsId)
                    .flatMap(Collection::stream)
                    .toList();
        }
        LOG.info("Getting directories to aggregate took {}", Duration.between(getDirectoriesStartTime, Instant.now()));
        LOG.info("Aggregating metrics for {} directories", s3DirectoriesToAggregate.size());
        if (s3DirectoriesToAggregate.isEmpty()) {
            LOG.info("No directories found to aggregate metrics");
            return;
        }

        if (aggregateMetricsCommand.isWithAthena()) {
            MetricsAggregatorAthenaClient metricsAggregatorAthenaClient = new MetricsAggregatorAthenaClient(config);
            metricsAggregatorAthenaClient.aggregateMetrics(s3DirectoriesToAggregate, extendedGa4GhApi, skipPostingToDockstore);
        } else {
            metricsAggregatorS3Client.aggregateMetrics(s3DirectoriesToAggregate, extendedGa4GhApi, skipPostingToDockstore);
        }
    }

    private void submitValidationData(MetricsAggregatorConfig config, ValidatorToolEnum validator, String validatorVersion, String dataFilePath, Partner platform, String executionId) throws IOException {
        ApiClient apiClient = setupApiClient(config.getDockstoreConfig().serverUrl(), config.getDockstoreConfig().token());
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
            validationExecution.setExecutionId(executionId);
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
