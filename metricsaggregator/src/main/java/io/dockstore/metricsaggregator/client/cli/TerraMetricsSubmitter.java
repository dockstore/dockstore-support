package io.dockstore.metricsaggregator.client.cli;

import static io.dockstore.utils.CLIConstants.FAILURE_EXIT_CODE;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;

import io.dockstore.common.Partner;
import io.dockstore.metricsaggregator.MetricsAggregatorConfig;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.SubmitTerraMetrics;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.SubmitTerraMetrics.SkippedTerraMetricsCsvHeaders;
import io.dockstore.metricsaggregator.client.cli.CommandLineArgs.SubmitTerraMetrics.TerraMetricsCsvHeaders;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum;
import io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraMetricsSubmitter {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsAggregatorClient.class);
    private final MetricsAggregatorConfig config;
    private final SubmitTerraMetrics submitTerraMetricsCommand;
    private final AtomicInteger numberOfExecutionsProcessed = new AtomicInteger(0);
    private final AtomicInteger numberOfExecutionsSubmitted = new AtomicInteger(0);
    private final AtomicInteger numberOfExecutionsSkipped = new AtomicInteger(0);
    private final AtomicInteger numberOfCacheHits = new AtomicInteger(0);
    private final AtomicInteger numberOfCacheMisses = new AtomicInteger(0);
    // Keep track of sourceUrls that are found to be ambiguous
    private final Map<String, String> sourceUrlsToSkipToReason = new ConcurrentHashMap<>();
    // Map of source url to TRS info that was previously calculated
    private final Map<String, SourceUrlTrsInfo> sourceUrlToSourceUrlTrsInfo = new ConcurrentHashMap<>();
    private final Map<String, List<MinimalWorkflowInfo>> workflowPathPrefixToWorkflows = new ConcurrentHashMap<>();

    public TerraMetricsSubmitter(MetricsAggregatorConfig config, SubmitTerraMetrics submitTerraMetricsCommand) {
        this.config = config;
        this.submitTerraMetricsCommand = submitTerraMetricsCommand;
    }

    public void submitMetrics() {
        ApiClient apiClient = setupApiClient(config.getDockstoreServerUrl(), config.getDockstoreToken());
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);

        // Read CSV file
        Iterable<CSVRecord> workflowMetricRecords = null;
        final String inputDateFilePath = this.submitTerraMetricsCommand.getDataFilePath();
        try (BufferedReader metricsBufferedReader = new BufferedReader(new FileReader(inputDateFilePath))) {
            //final Reader entriesCsv = new BufferedReader(new FileReader(inputDateFilePath));
            final CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(TerraMetricsCsvHeaders.class)
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build();
            workflowMetricRecords = csvFormat.parse(metricsBufferedReader);

            final String outputFileName = inputDateFilePath + "_skipped_executions_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";

            List<CSVRecord> workflowMetricsToProcess = new ArrayList<>();
            try (CSVPrinter skippedExecutionsCsvPrinter = submitTerraMetricsCommand.isRecordSkippedExecutions() ? new CSVPrinter(new FileWriter(outputFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(SkippedTerraMetricsCsvHeaders.class).build()) : null) {
                for (CSVRecord workflowMetricRecord: workflowMetricRecords) {
                    final int batchSize = 100000;
                    if (workflowMetricsToProcess.size() < batchSize && workflowMetricRecords.iterator().hasNext()) {
                        workflowMetricsToProcess.add(workflowMetricRecord);
                    } else {
                        workflowMetricsToProcess.add(workflowMetricRecord);
                        LOG.info("Processing {} rows", workflowMetricsToProcess.size());
                        workflowMetricsToProcess.stream()
                                .parallel()
                                .forEach(record -> processWorkflowExecution(record, workflowsApi, extendedGa4GhApi, skippedExecutionsCsvPrinter));
                        workflowMetricsToProcess.clear();
                    }
                }
            } catch (IOException e) {
                LOG.error("Unable to create new CSV output file", e);
                System.exit(FAILURE_EXIT_CODE);
            }

            LOG.info("Done processing {} executions from Terra. Submitted {} executions. Skipped {} executions. Cache hits: {}. Cache misses: {}", numberOfExecutionsProcessed, numberOfExecutionsSubmitted, numberOfExecutionsSkipped, numberOfCacheHits, numberOfCacheMisses);
            if (submitTerraMetricsCommand.isRecordSkippedExecutions()) {
                LOG.info("View skipped executions in file {}", outputFileName);
            }
        } catch (IOException e) {
            LOG.error("Unable to read input CSV file", e);
            System.exit(FAILURE_EXIT_CODE);
        }
    }

    private void processWorkflowExecution(CSVRecord workflowMetricRecord, WorkflowsApi workflowsApi, ExtendedGa4GhApi extendedGa4GhApi, CSVPrinter skippedExecutionsCsvPrinter) {
        final String sourceUrl = workflowMetricRecord.get(TerraMetricsCsvHeaders.source_url);
        LOG.info("Processing execution on row {} with source_url {}", workflowMetricRecord.getRecordNumber(), sourceUrl);
        numberOfExecutionsProcessed.incrementAndGet();

        // Check to see if this source_url was skipped before
        if (sourceUrlsToSkipToReason.containsKey(sourceUrl)) {
            skipExecution(sourceUrl, workflowMetricRecord, sourceUrlsToSkipToReason.get(sourceUrl), skippedExecutionsCsvPrinter);
            return;
        }

        // Check to see if we need to figure out the TRS ID for the source URL
        if (sourceUrlToSourceUrlTrsInfo.containsKey(sourceUrl)) {
            numberOfCacheHits.incrementAndGet();
        } else {
            numberOfCacheMisses.incrementAndGet();
            Optional<SourceUrlTrsInfo> sourceUrlTrsInfo = calculateTrsInfoFromSourceUrl(workflowMetricRecord, sourceUrl, workflowsApi, skippedExecutionsCsvPrinter);
            if (sourceUrlTrsInfo.isEmpty()) {
                return;
            } else {
                sourceUrlToSourceUrlTrsInfo.put(sourceUrl, sourceUrlTrsInfo.get());
            }
        }

        final Optional<RunExecution> workflowExecution = getTerraWorkflowExecutionFromCsvRecord(workflowMetricRecord, sourceUrl, skippedExecutionsCsvPrinter);
        if (workflowExecution.isEmpty()) {
            return;
        }

        final SourceUrlTrsInfo sourceUrlTrsInfo = sourceUrlToSourceUrlTrsInfo.get(sourceUrl);
        final ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody().runExecutions(List.of(workflowExecution.get()));
        try {
            extendedGa4GhApi.executionMetricsPost(executionsRequestBody, Partner.TERRA.toString(), sourceUrlTrsInfo.trsId(), sourceUrlTrsInfo.version(),
                    "Terra metrics from BigQuery table broad-dsde-prod-analytics-dev.externally_shared_metrics.dockstore_workflow_metrics_Q4_2023");
            numberOfExecutionsSubmitted.incrementAndGet();
        } catch (ApiException e) {
            skipExecution(sourceUrl, workflowMetricRecord, String.format("Could not submit execution metrics to Dockstore for workflow %s: %s", sourceUrlTrsInfo, e.getMessage()), skippedExecutionsCsvPrinter);
        }
    }

    static Optional<ExecutionStatusEnum> getExecutionStatusEnumFromTerraStatus(String terraStatus) {
        ExecutionStatusEnum executionStatusEnum = switch (terraStatus) {
        case "Succeeded" -> ExecutionStatusEnum.SUCCESSFUL;
        case "Failed" -> ExecutionStatusEnum.FAILED;
        case "Aborted" -> ExecutionStatusEnum.ABORTED;
        default -> null;
        };
        return Optional.ofNullable(executionStatusEnum);
    }

    static Optional<String> formatStringInIso8601Date(String workflowStart) {
        // Example workflow_start: 2022-07-15 15:37:06.450000 UTC
        final int maxNumberOfMicroSeconds = 9;
        final DateTimeFormatter workflowStartFormat = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .appendFraction(ChronoField.MICRO_OF_SECOND, 0, maxNumberOfMicroSeconds, true) // There are varying widths of micro seconds
                .optionalStart() // Start optional time zone pattern. Time zone is not always included
                .appendPattern(" ")
                .appendZoneId()
                .optionalEnd() // End optional time zone pattern
                .toFormatter();

        try {
            final LocalDateTime localDateTime = LocalDateTime.parse(workflowStart, workflowStartFormat);
            return Optional.of(DateTimeFormatter.ISO_INSTANT.format(localDateTime.atOffset(ZoneOffset.UTC)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private Optional<String> getPrimaryDescriptorAbsolutePath(WorkflowsApi workflowsApi, MinimalWorkflowInfo workflow, String version) {
        try {
            return Optional.of(workflowsApi.primaryDescriptor1(workflow.id(), version, workflow.descriptorType().toString()).getAbsolutePath());
        } catch (ApiException e) {
            return Optional.empty();
        }
    }


    private List<String> getSourceUrlComponents(String sourceUrl) {
        final String rawGitHubUserContentUrlPrefix = "https://raw.githubusercontent.com/"; // The source_url starts with this
        final String sourceUrlWithoutGitHubPrefix = sourceUrl.replace(rawGitHubUserContentUrlPrefix, "");
        return Arrays.stream(sourceUrlWithoutGitHubPrefix.split("/"))
                .filter(urlComponent -> !urlComponent.isEmpty()) // Filter out empty strings that are a result of consecutive slashes '//'
                .toList();
    }

    private void skipExecution(String sourceUrl, CSVRecord csvRecordToSkip, String reason, CSVPrinter skippedExecutionsCsvPrinter) {
        LOG.info("Skipping execution on row {}: {}", csvRecordToSkip.getRecordNumber(), reason);
        // Record to map for future reference
        sourceUrlsToSkipToReason.put(sourceUrl, reason);
        if (submitTerraMetricsCommand.isRecordSkippedExecutions()) {
            // Record to output CSV file for later examination
            // Headers: workflow_id, status, workflow_start, workflow_end, workflow_runtime_minutes, source_url
            List<String> csvColumnValues = Arrays.stream(TerraMetricsCsvHeaders.values())
                    .map(csvRecordToSkip::get) // Get the column value for the record
                    .collect(Collectors.toList());
            csvColumnValues.add(reason);
            try {
                skippedExecutionsCsvPrinter.printRecord(csvColumnValues);
            } catch (IOException e) {
                LOG.error("Could not write skipped execution to output file");
            }
        }
        numberOfExecutionsSkipped.incrementAndGet();
    }

    public Optional<RunExecution> getTerraWorkflowExecutionFromCsvRecord(CSVRecord csvRecord, String sourceUrl, CSVPrinter skippedExecutionsCsvPrinter) {
        final String executionId = csvRecord.get(TerraMetricsCsvHeaders.workflow_id);
        final String workflowStart = csvRecord.get(TerraMetricsCsvHeaders.workflow_start);
        final String status = csvRecord.get(TerraMetricsCsvHeaders.status);
        final String workflowRunTimeMinutes = csvRecord.get(TerraMetricsCsvHeaders.workflow_runtime_minutes);

        // Check that all required fields are non-null
        if (executionId == null || workflowStart == null || status == null) {
            skipExecution(sourceUrl, csvRecord, "One or more of the required fields (workflow_id, workflow_start, status) is missing", skippedExecutionsCsvPrinter);
            return Optional.empty();
        }

        // Format fields into Dockstore schema
        final Optional<ExecutionStatusEnum> executionStatus = getExecutionStatusEnumFromTerraStatus(status);
        if (executionStatus.isEmpty()) {
            skipExecution(sourceUrl, csvRecord, "Could not get a valid ExecutionStatusEnum from status '" + status + "'", skippedExecutionsCsvPrinter);
            return Optional.empty();
        }

        final Optional<String> dateExecuted = formatStringInIso8601Date(workflowStart);
        if (dateExecuted.isEmpty()) {
            skipExecution(sourceUrl, csvRecord, "Could not get a valid dateExecuted from workflow_start '" + workflowStart + "'", skippedExecutionsCsvPrinter);
            return Optional.empty();
        }

        RunExecution workflowExecution = new RunExecution();
        // TODO: uncomment below when the update executions endpoint PR is merged
        // workflowExecution.setExecutionId(executionId);
        workflowExecution.setExecutionStatus(executionStatus.get());
        workflowExecution.setDateExecuted(dateExecuted.get());
        getExecutionTime(workflowRunTimeMinutes).ifPresent(workflowExecution::setExecutionTime);
        return Optional.of(workflowExecution);
    }

    static Optional<String> getExecutionTime(String workflowRunTimeMinutes) {
        if (StringUtils.isBlank(workflowRunTimeMinutes)) {
            return Optional.empty();
        }
        return Optional.of(String.format("PT%sM", workflowRunTimeMinutes));
    }

    private Optional<SourceUrlTrsInfo> calculateTrsInfoFromSourceUrl(CSVRecord workflowMetricRecord, String sourceUrl, WorkflowsApi workflowsApi, CSVPrinter skippedExecutionsCsvPrinter) {
        // Need to figure out the TRS ID and version name using the source_url.
        // Example source_url: https://raw.githubusercontent.com/theiagen/public_health_viral_genomics/v2.0.0/workflows/wf_theiacov_fasta.wdl
        // Organization = "theiagen/public_health_viral_genomics", version = "v2.0.0", the rest is the primary descriptor path
        // Note that the TRS ID may also have a workflow name, which we need to figure out
        final List<String> sourceUrlComponents = getSourceUrlComponents(sourceUrl);

        // There should be at least three elements in order for there to be an organization name, foo>/<organization>, and version <version>
        // in <foo>/<organization>/<version>/<path-to-descriptor>
        final int minNumberOfComponents = 3;
        if (sourceUrlComponents.size() >= minNumberOfComponents) {
            final String organization = sourceUrlComponents.get(0) + "/" + sourceUrlComponents.get(1);
            final String version = sourceUrlComponents.get(2);
            final String primaryDescriptorPathFromUrl = "/" + String.join("/", sourceUrlComponents.subList(3, sourceUrlComponents.size()));

            final String workflowPathPrefix = "github.com/" + organization;
            if (!workflowPathPrefixToWorkflows.containsKey(workflowPathPrefix)) {
                try {
                    List<MinimalWorkflowInfo> publishedWorkflowsWithSamePathPrefix = workflowsApi.getAllPublishedWorkflowByPath(workflowPathPrefix).stream()
                            .map(workflow -> new MinimalWorkflowInfo(workflow.getId(), workflow.getFullWorkflowPath(), workflow.getDescriptorType(), new HashMap<>()))
                            .toList();
                    workflowPathPrefixToWorkflows.put(workflowPathPrefix, publishedWorkflowsWithSamePathPrefix);
                } catch (ApiException e) {
                    skipExecution(sourceUrl, workflowMetricRecord,
                            "Could not get all published workflows for workflow path " + workflowPathPrefix + " to determine TRS ID",
                            skippedExecutionsCsvPrinter);
                    return Optional.empty();
                }
            }

            List<MinimalWorkflowInfo> workflowsFromSameRepo = workflowPathPrefixToWorkflows.get(workflowPathPrefix);

            List<String> foundFullWorkflowPaths = new ArrayList<>();
            // Loop through each workflow to find one that matches the primary descriptor
            workflowsFromSameRepo.forEach(workflow -> {
                if (!workflow.versionToPrimaryDescriptorPath().containsKey(version)) {
                    // Intentionally putting null in map to indicate that the primary descriptor path for the workflow version doesn't exist
                    workflow.versionToPrimaryDescriptorPath().put(version, getPrimaryDescriptorAbsolutePath(workflowsApi, workflow, version).orElse(null));
                }

                final String primaryDescriptorPathForVersion = workflow.versionToPrimaryDescriptorPath().get(version);
                if (primaryDescriptorPathFromUrl.equals(primaryDescriptorPathForVersion)) {
                    // Collect a list of workflow paths that have the primary descriptor path we're looking for
                    foundFullWorkflowPaths.add(workflow.fullWorkflowPath());
                }
            });


            if (foundFullWorkflowPaths.isEmpty()) {
                skipExecution(sourceUrl, workflowMetricRecord, "Could not find workflow with primary descriptor " + primaryDescriptorPathFromUrl, skippedExecutionsCsvPrinter);
                return Optional.empty();
            } else if (foundFullWorkflowPaths.size() > 1) {
                // There is already a workflow in the same repository with the same descriptor path that we're looking for.
                // Skip this source url because it is an ambiguous case and we can't identify which workflow the source url is referring to.
                skipExecution(sourceUrl, workflowMetricRecord, String.format("There's %s workflows in the repository with the same primary descriptor path '%s': %s", foundFullWorkflowPaths.size(), primaryDescriptorPathFromUrl, foundFullWorkflowPaths), skippedExecutionsCsvPrinter);
                return Optional.empty();
            } else {
                final SourceUrlTrsInfo sourceUrlTrsInfo = new SourceUrlTrsInfo(sourceUrl, "#workflow/" + foundFullWorkflowPaths.get(0), version);
                return Optional.of(sourceUrlTrsInfo);
            }

        } else {
            skipExecution(sourceUrl, workflowMetricRecord, "Not enough components in the source_url to figure out the TRS ID and version", skippedExecutionsCsvPrinter);
            return Optional.empty();
        }
    }

    /**
     * Record that stores the calculated TRS ID and version, derived from the source_url
     * @param sourceUrl
     * @param trsId
     * @param version
     */
    public record SourceUrlTrsInfo(String sourceUrl, String trsId, String version) {
    }

    public record MinimalWorkflowInfo(long id, String fullWorkflowPath, DescriptorTypeEnum descriptorType, Map<String, String> versionToPrimaryDescriptorPath) {
    }
}
