package io.dockstore.metricsaggregator.client.cli;

import static io.dockstore.utils.CLIConstants.FAILURE_EXIT_CODE;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;
import static java.util.stream.Collectors.groupingBy;

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
    private static final int MAX_NUMBER_OF_MICRO_SECONDS = 9;
    // Example workflow_start: 2022-07-15 15:37:06.450000 UTC
    private static final DateTimeFormatter WORKFLOW_START_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .appendFraction(ChronoField.MICRO_OF_SECOND, 0, MAX_NUMBER_OF_MICRO_SECONDS, true) // There are varying widths of micro seconds
            .optionalStart() // Start optional time zone pattern. Time zone is not always included
            .appendPattern(" ")
            .appendZoneId()
            .optionalEnd() // End optional time zone pattern
            .toFormatter();
    private final MetricsAggregatorConfig config;
    private final SubmitTerraMetrics submitTerraMetricsCommand;
    private final AtomicInteger numberOfExecutionsProcessed = new AtomicInteger(0);
    private final AtomicInteger numberOfExecutionsSubmitted = new AtomicInteger(0);
    private final AtomicInteger numberOfExecutionsSkipped = new AtomicInteger(0);

    // Keep track of sourceUrls that are found to be ambiguous
    private final Map<String, String> skippedSourceUrlsToReason = new ConcurrentHashMap<>();
    // Map of source url to TRS info that was previously calculated
    private final Map<String, SourceUrlTrsInfo> sourceUrlToSourceUrlTrsInfo = new ConcurrentHashMap<>();
    // Map of workflow path prefixes, like github.com/organization/repo, to published workflows with the same workflow path prefix
    private final Map<String, List<MinimalWorkflowInfo>> workflowPathPrefixToWorkflows = new ConcurrentHashMap<>();

    public TerraMetricsSubmitter(MetricsAggregatorConfig config, SubmitTerraMetrics submitTerraMetricsCommand) {
        this.config = config;
        this.submitTerraMetricsCommand = submitTerraMetricsCommand;
    }

    public void submitTerraMetrics() {
        ApiClient apiClient = setupApiClient(config.getDockstoreServerUrl(), config.getDockstoreToken());
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);

        // Read CSV file
        Iterable<CSVRecord> workflowMetricRecords;
        final String inputDateFilePath = this.submitTerraMetricsCommand.getDataFilePath();
        try (BufferedReader metricsBufferedReader = new BufferedReader(new FileReader(inputDateFilePath))) {
            final CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(TerraMetricsCsvHeaders.class)
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build();
            workflowMetricRecords = csvFormat.parse(metricsBufferedReader);

            // This output file is used to record skipped executions
            final String outputFileName = inputDateFilePath + "_skipped_executions_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";

            List<CSVRecord> workflowMetricsToProcess = new ArrayList<>();
            try (CSVPrinter skippedExecutionsCsvPrinter = submitTerraMetricsCommand.isRecordSkippedExecutions() ? new CSVPrinter(
                    new FileWriter(outputFileName, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder().setHeader(SkippedTerraMetricsCsvHeaders.class).build()) : null) {

                // Process the executions by reading 100,000 rows, then processing the rows in parallel
                for (CSVRecord workflowMetricRecord: workflowMetricRecords) {
                    final int batchSize = 100000;
                    if (workflowMetricsToProcess.size() < batchSize && workflowMetricRecords.iterator().hasNext()) {
                        workflowMetricsToProcess.add(workflowMetricRecord);
                    } else {
                        workflowMetricsToProcess.add(workflowMetricRecord);
                        // Collect a map of CSV records with the same source URL
                        Map<String, List<CSVRecord>> sourceUrlToCsvRecords = workflowMetricsToProcess.stream()
                                .collect(groupingBy(csvRecord -> csvRecord.get(TerraMetricsCsvHeaders.source_url)));
                        /*
                        for (CSVRecord workflowMetricToProcess: workflowMetricsToProcess) {
                            final String csvRecordSourceUrl = workflowMetricToProcess.get(TerraMetricsCsvHeaders.source_url);
                            List<CSVRecord> csvRecordsWithSameSourceUrl = sourceUrlToCsvRecords.getOrDefault(csvRecordSourceUrl, new ArrayList<>());
                            csvRecordsWithSameSourceUrl.add(workflowMetricToProcess);
                            sourceUrlToCsvRecords.put(csvRecordSourceUrl, csvRecordsWithSameSourceUrl);
                        }
                         */

                        LOG.info("Processing rows {} to {}", workflowMetricsToProcess.get(0).getRecordNumber(), workflowMetricsToProcess.get(workflowMetricsToProcess.size() - 1).getRecordNumber());

                        sourceUrlToCsvRecords.entrySet().stream()
                                .parallel()
                                .forEach(entry -> {
                                    final String sourceUrl = entry.getKey();
                                    final List<CSVRecord> csvRecordsWithSameSourceUrl = entry.getValue();
                                    submitWorkflowExecutions(sourceUrl, csvRecordsWithSameSourceUrl, workflowsApi, extendedGa4GhApi, skippedExecutionsCsvPrinter);
                                });

                        workflowMetricsToProcess.clear();
                        logStats();
                    }
                }
            } catch (IOException e) {
                LOG.error("Unable to create new CSV output file", e);
                System.exit(FAILURE_EXIT_CODE);
            }

            logStats();

            if (submitTerraMetricsCommand.isRecordSkippedExecutions()) {
                LOG.info("View skipped executions in file {}", outputFileName);
            }
        } catch (IOException e) {
            LOG.error("Unable to read input CSV file", e);
            System.exit(FAILURE_EXIT_CODE);
        }
    }

    private void logStats() {
        LOG.info("Done processing {} executions from Terra. Submitted {} executions. Skipped {} executions.", numberOfExecutionsProcessed, numberOfExecutionsSubmitted, numberOfExecutionsSkipped);
    }

    /**
     * Processes the workflow execution from the CSV record and submits the execution to Dockstore if it is valid.
     * @param workflowMetricRecord
     * @param workflowsApi
     * @param extendedGa4GhApi
     * @param skippedExecutionsCsvPrinter
     */
    private void processWorkflowExecution(CSVRecord workflowMetricRecord, WorkflowsApi workflowsApi, ExtendedGa4GhApi extendedGa4GhApi, CSVPrinter skippedExecutionsCsvPrinter) {
        final String sourceUrl = workflowMetricRecord.get(TerraMetricsCsvHeaders.source_url);
        LOG.info("Processing execution on row {} with source_url {}", workflowMetricRecord.getRecordNumber(), sourceUrl);
        numberOfExecutionsProcessed.incrementAndGet();

        if (StringUtils.isBlank(sourceUrl)) {
            logSkippedExecution("", workflowMetricRecord, "Can't determine TRS ID because source_url is missing", skippedExecutionsCsvPrinter, false);
        }

        // Check to see if this source_url was skipped before
        if (skippedSourceUrlsToReason.containsKey(sourceUrl)) {
            logSkippedExecution(sourceUrl, workflowMetricRecord, skippedSourceUrlsToReason.get(sourceUrl), skippedExecutionsCsvPrinter, true);
            return;
        }

        if (!sourceUrlToSourceUrlTrsInfo.containsKey(sourceUrl)) {
            LOG.info("Calculating TRS info for source_url {}", sourceUrl);
            Optional<SourceUrlTrsInfo> sourceUrlTrsInfo = calculateTrsInfoFromSourceUrl(workflowMetricRecord, sourceUrl, workflowsApi, skippedExecutionsCsvPrinter);
            if (sourceUrlTrsInfo.isEmpty()) {
                return;
            } else {
                sourceUrlToSourceUrlTrsInfo.put(sourceUrl, sourceUrlTrsInfo.get());
            }
        } else {
            LOG.info("TRS info for source_url {} was previously calculated", sourceUrl);
        }

        // Check if the information from the workflow execution is valid
        Optional<RunExecution> workflowExecution = getTerraWorkflowExecutionFromCsvRecord(workflowMetricRecord, sourceUrl, skippedExecutionsCsvPrinter);
        if (workflowExecution.isEmpty()) {
            return;
        }

        final SourceUrlTrsInfo sourceUrlTrsInfo = sourceUrlToSourceUrlTrsInfo.get(sourceUrl);
        final ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody().runExecutions(List.of(workflowExecution.get()));
        try {
            extendedGa4GhApi.executionMetricsPost(executionsRequestBody, Partner.TERRA.toString(), sourceUrlTrsInfo.trsId(), sourceUrlTrsInfo.version(),
                    "Terra metrics from Q4 2023");
            numberOfExecutionsSubmitted.incrementAndGet();
        } catch (ApiException e) {
            logSkippedExecution(sourceUrl, workflowMetricRecord, String.format("Could not submit execution metrics to Dockstore for workflow %s: %s", sourceUrlTrsInfo, e.getMessage()), skippedExecutionsCsvPrinter, false);
        }
    }

    private void submitWorkflowExecutions(String sourceUrl, List<CSVRecord> workflowMetricRecords, WorkflowsApi workflowsApi, ExtendedGa4GhApi extendedGa4GhApi, CSVPrinter skippedExecutionsCsvPrinter) {
        LOG.info("Processing source_url {} for {} executions", sourceUrl, workflowMetricRecords.size());
        numberOfExecutionsProcessed.addAndGet(workflowMetricRecords.size());

        if (StringUtils.isBlank(sourceUrl)) {
            workflowMetricRecords.forEach(workflowMetricRecord -> {
                logSkippedExecution("", workflowMetricRecord, "Can't determine TRS ID because source_url is missing", skippedExecutionsCsvPrinter, false);
            });
        }

        // Check to see if this source_url was skipped before
        if (skippedSourceUrlsToReason.containsKey(sourceUrl)) {
            workflowMetricRecords.forEach(workflowMetricRecord -> {
                logSkippedExecution(sourceUrl, workflowMetricRecord, skippedSourceUrlsToReason.get(sourceUrl), skippedExecutionsCsvPrinter, true);
            });
            return;
        }

        if (!sourceUrlToSourceUrlTrsInfo.containsKey(sourceUrl)) {
            Optional<SourceUrlTrsInfo> sourceUrlTrsInfo = calculateTrsInfoFromSourceUrl(sourceUrl, workflowsApi);
            if (sourceUrlTrsInfo.isEmpty()) {
                workflowMetricRecords.forEach(workflowMetricRecord -> {
                    logSkippedExecution(sourceUrl, workflowMetricRecord, "Could not calculate TRS info", skippedExecutionsCsvPrinter, true);
                });
                return;
            } else {
                sourceUrlToSourceUrlTrsInfo.put(sourceUrl, sourceUrlTrsInfo.get());
            }
        }

        final SourceUrlTrsInfo sourceUrlTrsInfo = sourceUrlToSourceUrlTrsInfo.get(sourceUrl);
        final List<RunExecution> workflowExecutionsToSubmit = workflowMetricRecords.stream()
                .map(workflowExecution -> getTerraWorkflowExecutionFromCsvRecord(workflowExecution, sourceUrlTrsInfo.sourceUrl(), skippedExecutionsCsvPrinter))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        final ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody().runExecutions(workflowExecutionsToSubmit);
        try {
            extendedGa4GhApi.executionMetricsPost(executionsRequestBody, Partner.TERRA.toString(), sourceUrlTrsInfo.trsId(), sourceUrlTrsInfo.version(),
                    "Terra metrics from Q4 2023");
            numberOfExecutionsSubmitted.addAndGet(workflowMetricRecords.size());
        } catch (ApiException e) {
            workflowMetricRecords.forEach(workflowMetricRecord -> {
                logSkippedExecution(sourceUrlTrsInfo.sourceUrl(), workflowMetricRecord, String.format("Could not submit execution metrics to Dockstore for workflow %s: %s", sourceUrlTrsInfo, e.getMessage()), skippedExecutionsCsvPrinter, false);
            });
        }
    }

    /**
     * Performs logging and writing of the skipped execution to an output file.
     * If skipFutureExecutionsWithSourceUrl is true, also adds the source_url of the skipped execution to the sourceUrlsToSkipToReason map
     * so that future executions with the same source_url are skipped.
     * @param sourceUrl source_url of the csvRecordToSkip
     * @param csvRecordToSkip CSVRecord to skip
     * @param reason Reason that this execution is being skipped
     * @param skippedExecutionsCsvPrinter CSVPrinter that writes to the output file
     * @param skipFutureExecutionsWithSourceUrl boolean indicating if all executions with the same source_url should be skipped
     */
    private void logSkippedExecution(String sourceUrl, CSVRecord csvRecordToSkip, String reason, CSVPrinter skippedExecutionsCsvPrinter, boolean skipFutureExecutionsWithSourceUrl) {
        // Record to map for future reference. Only want to do this if the skip reason applies for ALL executions with the source_url.
        // Should not add to this map if the skip reason is specific to one execution
        if (skipFutureExecutionsWithSourceUrl) {
            skippedSourceUrlsToReason.put(sourceUrl, reason);
        }
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

    /**
     * Gets a RunExecution representing a single Terra workflow execution from one row of the CSV file.
     * If the CSV record is invalid, the function will record the reason why the execution was skipped using skippedExecutionsCsvPrinter.
     * Note: If an execution is skipped in this function, it means that the reason is specific to the execution, not the source_url!
     * @param csvRecord
     * @param sourceUrl
     * @param skippedExecutionsCsvPrinter
     * @return
     */
    public Optional<RunExecution> getTerraWorkflowExecutionFromCsvRecord(CSVRecord csvRecord, String sourceUrl, CSVPrinter skippedExecutionsCsvPrinter) {
        final String executionId = csvRecord.get(TerraMetricsCsvHeaders.workflow_id);
        final String workflowStart = csvRecord.get(TerraMetricsCsvHeaders.workflow_start);
        final String status = csvRecord.get(TerraMetricsCsvHeaders.status);
        final String workflowRunTimeMinutes = csvRecord.get(TerraMetricsCsvHeaders.workflow_runtime_minutes);

        // Check that all required fields are present
        if (StringUtils.isBlank(executionId)) {
            logSkippedExecution(sourceUrl, csvRecord, "The required field workflow_id is missing", skippedExecutionsCsvPrinter, false);
            return Optional.empty();
        }

        if (StringUtils.isBlank(workflowStart)) {
            logSkippedExecution(sourceUrl, csvRecord, "The required field workflow_start is missing", skippedExecutionsCsvPrinter, false);
            return Optional.empty();
        }

        if (StringUtils.isBlank(status)) {
            logSkippedExecution(sourceUrl, csvRecord, "The required field status is missing", skippedExecutionsCsvPrinter, false);
            return Optional.empty();
        }

        // Format fields into Dockstore schema
        final Optional<ExecutionStatusEnum> executionStatus = getExecutionStatusEnumFromTerraStatus(status);
        if (executionStatus.isEmpty()) {
            logSkippedExecution(sourceUrl, csvRecord, "Could not get a valid ExecutionStatusEnum from status '" + status + "'", skippedExecutionsCsvPrinter, false);
            return Optional.empty();
        }

        final Optional<String> dateExecuted = formatStringInIso8601Date(workflowStart);
        if (dateExecuted.isEmpty()) {
            logSkippedExecution(sourceUrl, csvRecord, "Could not get a valid dateExecuted from workflow_start '" + workflowStart + "'", skippedExecutionsCsvPrinter, false);
            return Optional.empty();
        }

        RunExecution workflowExecution = new RunExecution();
        // TODO: uncomment below when the update executions endpoint PR is merged
        //workflowExecution.setExecutionId(executionId);
        workflowExecution.setExecutionStatus(executionStatus.get());
        workflowExecution.setDateExecuted(dateExecuted.get());
        getExecutionTime(workflowRunTimeMinutes).ifPresent(workflowExecution::setExecutionTime);
        return Optional.of(workflowExecution);
    }

    static Optional<String> formatStringInIso8601Date(String workflowStart) {
        try {
            final LocalDateTime localDateTime = LocalDateTime.parse(workflowStart, WORKFLOW_START_FORMAT);
            return Optional.of(DateTimeFormatter.ISO_INSTANT.format(localDateTime.atOffset(ZoneOffset.UTC)));
        } catch (DateTimeParseException e) {
            LOG.error("Could not format workflow_start '{}' in ISO 8601 date format", workflowStart, e);
            return Optional.empty();
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

    static Optional<String> getExecutionTime(String workflowRunTimeMinutes) {
        if (StringUtils.isBlank(workflowRunTimeMinutes)) {
            return Optional.empty();
        }
        return Optional.of(String.format("PT%sM", workflowRunTimeMinutes));
    }

    /**
     * Calculates the TRS ID from the source_url by:
     * <ol>
     *     <li>Looking at all published workflows that have the same workflow path prefix, i.e. they belong to the same GitHub repository.</li>
     *     <li>For each published workflow, getting the primary descriptor path for the version specified in source_url and checking if it matches the primary desciptor path in the source_url</li>
     *     <li>Ensuring that there is only one workflow in the repository with the same descriptor path. If there are multiple, it is an ambiguous case and we skip the execution</li>
     * </ol>
     *
     * @param workflowMetricRecord
     * @param sourceUrl
     * @param workflowsApi
     * @param skippedExecutionsCsvPrinter
     * @return
     */
    private Optional<SourceUrlTrsInfo> calculateTrsInfoFromSourceUrl(CSVRecord workflowMetricRecord, String sourceUrl, WorkflowsApi workflowsApi, CSVPrinter skippedExecutionsCsvPrinter) {
        // Need to figure out the TRS ID and version name using the source_url.
        // Example source_url: https://raw.githubusercontent.com/theiagen/public_health_viral_genomics/v2.0.0/workflows/wf_theiacov_fasta.wdl
        // Organization = "theiagen/public_health_viral_genomics", version = "v2.0.0", the rest is the primary descriptor path
        // Note that the TRS ID may also have a workflow name, which we need to figure out
        final List<String> sourceUrlComponents = getSourceUrlComponents(sourceUrl);


        final int minNumberOfComponents = 3;
        if (sourceUrlComponents.size() < minNumberOfComponents) {
            logSkippedExecution(sourceUrl, workflowMetricRecord, "Not enough components in the source_url to figure out the TRS ID and version", skippedExecutionsCsvPrinter, true);
            return Optional.empty();
        }

        // There should be at least three elements in order for there to be an organization name, foo>/<organization>, and version <version>
        // in <foo>/<organization>/<version>/<path-to-descriptor>
        final String organization = sourceUrlComponents.get(0) + "/" + sourceUrlComponents.get(1);
        final String version = sourceUrlComponents.get(2);
        final String primaryDescriptorPathFromUrl = "/" + String.join("/", sourceUrlComponents.subList(3, sourceUrlComponents.size()));

        final String workflowPathPrefix = "github.com/" + organization;
        if (!workflowPathPrefixToWorkflows.containsKey(workflowPathPrefix)) {
            try {
                List<MinimalWorkflowInfo> publishedWorkflowsWithSamePathPrefix = workflowsApi.getAllPublishedWorkflowByPath(
                                workflowPathPrefix).stream()
                        .map(workflow -> new MinimalWorkflowInfo(workflow.getId(), workflow.getFullWorkflowPath(), workflow.getDescriptorType(), new ConcurrentHashMap<>())).toList();
                workflowPathPrefixToWorkflows.put(workflowPathPrefix, publishedWorkflowsWithSamePathPrefix);
            } catch (ApiException e) {
                logSkippedExecution(sourceUrl, workflowMetricRecord,
                        "Could not get all published workflows for workflow path " + workflowPathPrefix + " to determine TRS ID",
                        skippedExecutionsCsvPrinter, true);
                return Optional.empty();
            }
        }

        List<MinimalWorkflowInfo> workflowsFromSameRepo = workflowPathPrefixToWorkflows.get(workflowPathPrefix);

        List<String> foundFullWorkflowPaths = new ArrayList<>();
        // Loop through each workflow to find one that matches the primary descriptor
        workflowsFromSameRepo.forEach(workflow -> {
            // Get the primary descriptor path for the version and update the map, either with the primary descriptor path or an empty string to indicate that it was not found
            if (!workflow.versionToPrimaryDescriptorPath().containsKey(version)) {
                final String primaryDescriptorAbsolutePath = makePathAbsolute(getPrimaryDescriptorAbsolutePath(workflowsApi, workflow, version).orElse(""));
                workflow.versionToPrimaryDescriptorPath().put(version, primaryDescriptorAbsolutePath);
            }

            // Check to see if there's a version that has the same primary descriptor path
            final String primaryDescriptorPathForVersion = workflow.versionToPrimaryDescriptorPath().get(version);
            if (primaryDescriptorPathFromUrl.equals(primaryDescriptorPathForVersion)) {
                foundFullWorkflowPaths.add(workflow.fullWorkflowPath());
            }
        });

        if (foundFullWorkflowPaths.isEmpty()) {
            logSkippedExecution(sourceUrl, workflowMetricRecord, "Could not find workflow with primary descriptor " + primaryDescriptorPathFromUrl, skippedExecutionsCsvPrinter,
                    true);
            return Optional.empty();
        } else if (foundFullWorkflowPaths.size() > 1) {
            // There is already a workflow in the same repository with the same descriptor path that we're looking for.
            // Skip this source_url because it is an ambiguous case and we can't identify which workflow the source url is referring to.
            logSkippedExecution(sourceUrl, workflowMetricRecord, String.format("There's %s workflows in the repository with the same primary descriptor path '%s': %s",
                    foundFullWorkflowPaths.size(), primaryDescriptorPathFromUrl, foundFullWorkflowPaths), skippedExecutionsCsvPrinter, true);
            return Optional.empty();
        } else {
            final SourceUrlTrsInfo sourceUrlTrsInfo = new SourceUrlTrsInfo(sourceUrl, "#workflow/" + foundFullWorkflowPaths.get(0), version);
            return Optional.of(sourceUrlTrsInfo);
        }
    }

    /**
     * Calculates the TRS ID from the source_url by:
     * <ol>
     *     <li>Looking at all published workflows that have the same workflow path prefix, i.e. they belong to the same GitHub repository.</li>
     *     <li>For each published workflow, getting the primary descriptor path for the version specified in source_url and checking if it matches the primary desciptor path in the source_url</li>
     *     <li>Ensuring that there is only one workflow in the repository with the same descriptor path. If there are multiple, it is an ambiguous case and we skip the execution</li>
     * </ol>
     *
     * @param sourceUrl
     * @param workflowsApi
     * @return
     */
    private Optional<SourceUrlTrsInfo> calculateTrsInfoFromSourceUrl(String sourceUrl, WorkflowsApi workflowsApi) {
        // Need to figure out the TRS ID and version name using the source_url.
        // Example source_url: https://raw.githubusercontent.com/theiagen/public_health_viral_genomics/v2.0.0/workflows/wf_theiacov_fasta.wdl
        // Organization = "theiagen/public_health_viral_genomics", version = "v2.0.0", the rest is the primary descriptor path
        // Note that the TRS ID may also have a workflow name, which we need to figure out
        final List<String> sourceUrlComponents = getSourceUrlComponents(sourceUrl);


        final int minNumberOfComponents = 3;
        if (sourceUrlComponents.size() < minNumberOfComponents) {
            //(sourceUrl, workflowMetricRecord, "Not enough components in the source_url to figure out the TRS ID and version", skippedExecutionsCsvPrinter, true);
            return Optional.empty();
        }

        // There should be at least three elements in order for there to be an organization name, foo>/<organization>, and version <version>
        // in <foo>/<organization>/<version>/<path-to-descriptor>
        final String organization = sourceUrlComponents.get(0) + "/" + sourceUrlComponents.get(1);
        final String version = sourceUrlComponents.get(2);
        final String primaryDescriptorPathFromUrl = "/" + String.join("/", sourceUrlComponents.subList(3, sourceUrlComponents.size()));

        final String workflowPathPrefix = "github.com/" + organization;
        if (!workflowPathPrefixToWorkflows.containsKey(workflowPathPrefix)) {
            try {
                List<MinimalWorkflowInfo> publishedWorkflowsWithSamePathPrefix = workflowsApi.getAllPublishedWorkflowByPath(
                                workflowPathPrefix).stream()
                        .map(workflow -> new MinimalWorkflowInfo(workflow.getId(), workflow.getFullWorkflowPath(), workflow.getDescriptorType(), new ConcurrentHashMap<>())).toList();
                workflowPathPrefixToWorkflows.put(workflowPathPrefix, publishedWorkflowsWithSamePathPrefix);
            } catch (ApiException e) {
                /*
                logSkippedExecution(sourceUrl, workflowMetricRecord,
                        "Could not get all published workflows for workflow path " + workflowPathPrefix + " to determine TRS ID",
                        skippedExecutionsCsvPrinter, true);
                 */
                return Optional.empty();
            }
        }

        List<MinimalWorkflowInfo> workflowsFromSameRepo = workflowPathPrefixToWorkflows.get(workflowPathPrefix);

        List<String> foundFullWorkflowPaths = new ArrayList<>();
        // Loop through each workflow to find one that matches the primary descriptor
        workflowsFromSameRepo.forEach(workflow -> {
            // Get the primary descriptor path for the version and update the map, either with the primary descriptor path or an empty string to indicate that it was not found
            if (!workflow.versionToPrimaryDescriptorPath().containsKey(version)) {
                final String primaryDescriptorAbsolutePath = makePathAbsolute(getPrimaryDescriptorAbsolutePath(workflowsApi, workflow, version).orElse(""));
                workflow.versionToPrimaryDescriptorPath().put(version, primaryDescriptorAbsolutePath);
            }

            // Check to see if there's a version that has the same primary descriptor path
            final String primaryDescriptorPathForVersion = workflow.versionToPrimaryDescriptorPath().get(version);
            if (primaryDescriptorPathFromUrl.equals(primaryDescriptorPathForVersion)) {
                foundFullWorkflowPaths.add(workflow.fullWorkflowPath());
            }
        });

        if (foundFullWorkflowPaths.isEmpty()) {
            //logSkippedExecution(sourceUrl, workflowMetricRecord, "Could not find workflow with primary descriptor " + primaryDescriptorPathFromUrl, skippedExecutionsCsvPrinter, true);
            return Optional.empty();
        } else if (foundFullWorkflowPaths.size() > 1) {
            // There is already a workflow in the same repository with the same descriptor path that we're looking for.
            // Skip this source_url because it is an ambiguous case and we can't identify which workflow the source url is referring to.
            /*
            logSkippedExecution(sourceUrl, workflowMetricRecord, String.format("There's %s workflows in the repository with the same primary descriptor path '%s': %s",
                    foundFullWorkflowPaths.size(), primaryDescriptorPathFromUrl, foundFullWorkflowPaths), skippedExecutionsCsvPrinter, true);
             */
            return Optional.empty();
        } else {
            final SourceUrlTrsInfo sourceUrlTrsInfo = new SourceUrlTrsInfo(sourceUrl, "#workflow/" + foundFullWorkflowPaths.get(0), version);
            return Optional.of(sourceUrlTrsInfo);
        }
    }

    /**
     * Returns a list of slash-delimited components from the source_url.
     * Example: given source_url https://raw.githubusercontent.com/theiagen/public_health_viral_genomics/v2.0.0/workflows/wf_theiacov_fasta.wdl,
     * returns a list of "theiagen", "public_health_viral_genomics", "v2.0.0", "wf_theiacov_fasta.wdl"
     * @param sourceUrl The source_url that starts with https://raw.githubusercontent.com/.
     * @return
     */
    static List<String> getSourceUrlComponents(String sourceUrl) {
        final String sourceUrlWithoutGitHubPrefix = sourceUrl.replace("https://raw.githubusercontent.com/", "");
        return Arrays.stream(sourceUrlWithoutGitHubPrefix.split("/"))
                .filter(urlComponent -> !urlComponent.isEmpty()) // Filter out empty strings that are a result of consecutive slashes '//'
                .toList();
    }

    private Optional<String> getPrimaryDescriptorAbsolutePath(WorkflowsApi workflowsApi, MinimalWorkflowInfo workflow, String version) {
        Optional<String> primaryDescriptorPath = Optional.empty();
        try {
            primaryDescriptorPath = Optional.of(workflowsApi.primaryDescriptor1(workflow.id(), version, workflow.descriptorType().toString()).getAbsolutePath());
        } catch (ApiException e) {
            LOG.debug("Could not get primary descriptor path", e);
        }
        return primaryDescriptorPath;
    }

    static String makePathAbsolute(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Record that stores the calculated TRS ID and version, derived from the source_url
     * @param sourceUrl
     * @param trsId
     * @param version
     */
    public record SourceUrlTrsInfo(String sourceUrl, String trsId, String version) {
    }

    public record MinimalWorkflowInfo(long id, String fullWorkflowPath, DescriptorTypeEnum descriptorType, ConcurrentHashMap<String, String> versionToPrimaryDescriptorPath) {
    }
}