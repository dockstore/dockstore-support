package io.dockstore.metricsaggregator;

import static io.dockstore.metricsaggregator.helper.AthenaClientHelper.createAthenaClient;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;

import io.dockstore.common.Partner;
import io.dockstore.metricsaggregator.MetricsAggregatorS3Client.S3DirectoryInfo;
import io.dockstore.metricsaggregator.helper.AthenaAggregator;
import io.dockstore.metricsaggregator.helper.AthenaClientHelper;
import io.dockstore.metricsaggregator.helper.ExecutionStatusAthenaAggregator;
import io.dockstore.metricsaggregator.helper.ValidationStatusAthenaAggregator;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

/**
 * A class that aggregates metrics using AWS Athena.
 */
public class MetricsAggregatorAthenaClient {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsAggregatorAthenaClient.class);

    private final ExecutionStatusAthenaAggregator executionStatusAggregator;
    private final ValidationStatusAthenaAggregator validationStatusAggregator;
    private final String metricsBucketName;
    private final String athenaWorkgroup;
    private final String databaseName;
    private final String tableName;
    private final AthenaClient athenaClient;
    private final MetadataApi metadataApi;

    public MetricsAggregatorAthenaClient(MetricsAggregatorConfig config) {
        this.metricsBucketName = config.getS3Config().bucket();
        this.athenaWorkgroup = config.getAthenaConfig().workgroup();

        final String underscoredMetricsBucketName = metricsBucketName.replace("-", "_"); // The metrics bucket name is usually in the form of "env-dockstore-metrics-data"
        this.databaseName = underscoredMetricsBucketName + "_database";
        this.tableName = underscoredMetricsBucketName + "_table";
        this.athenaClient = createAthenaClient();
        this.metadataApi = new MetadataApi(setupApiClient(config.getDockstoreConfig().serverUrl())); // Anonymous client
        this.executionStatusAggregator = new ExecutionStatusAthenaAggregator(this, tableName);
        this.validationStatusAggregator = new ValidationStatusAthenaAggregator(this, tableName);
    }

    /**
     * Aggregate metrics using AWS Athena for the list of S3 directories and posts them to Dockstore.
     * @param s3DirectoriesToAggregate
     * @param extendedGa4GhApi
     */
    public void aggregateMetrics(List<S3DirectoryInfo> s3DirectoriesToAggregate, ExtendedGa4GhApi extendedGa4GhApi, int threadCount) {
        AthenaAggregator.createDatabase(databaseName, this);
        AthenaAggregator.createTable(tableName, metricsBucketName, metadataApi, this);

        aggregateVersionLevelMetrics(s3DirectoriesToAggregate, extendedGa4GhApi, threadCount);
        List<S3DirectoryInfo> entryDirectories = calculateEntryDirectories(s3DirectoriesToAggregate);
        aggregateEntryLevelMetrics(entryDirectories, extendedGa4GhApi, threadCount);
    }

    private void aggregateVersionLevelMetrics(List<S3DirectoryInfo> s3DirectoriesToAggregate, ExtendedGa4GhApi extendedGa4GhApi, int threadCount) {
        // Aggregate metrics for each directory
        AtomicInteger numberOfDirectoriesProcessed = new AtomicInteger(0);
        AtomicInteger numberOfVersionsSubmitted = new AtomicInteger(0);
        AtomicInteger numberOfVersionsSkipped = new AtomicInteger(0);

        LOG.info("Aggregating verson-level metrics using {} threads in parallel", threadCount);
        List<Runnable> runnables = s3DirectoriesToAggregate.stream().<Runnable>map(s3DirectoryInfo ->
            () -> {
                AthenaTablePartition partition = s3DirectoryInfo.athenaTablePartition();
                List<String> platforms = s3DirectoryInfo.platforms();
                String prefix = s3DirectoryInfo.versionS3KeyPrefix();
                String name = "tool ID %s, version %s".formatted(s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId());
                Map<String, Metrics> platformToMetrics = getAggregatedMetricsForPlatforms(partition, platforms, prefix, name);
                if (platformToMetrics.isEmpty()) {
                    LOG.error("No metrics were aggregated for {}", name);
                    numberOfVersionsSkipped.incrementAndGet();
                }

                try {
                    extendedGa4GhApi.aggregatedMetricsPut(platformToMetrics, s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId());
                    LOG.info("Posted aggregated metrics to Dockstore for {}, platform(s): {}", name, platformToMetrics.keySet());
                    numberOfVersionsSubmitted.incrementAndGet();
                } catch (ApiException exception) {
                    // Log error and continue processing for other platforms
                    LOG.error("Could not post aggregated metrics to Dockstore for {}, platform(s): {}", name, platformToMetrics.keySet(), exception);
                    numberOfVersionsSkipped.incrementAndGet();
                }
                numberOfDirectoriesProcessed.incrementAndGet();
                LOG.info("Processed {} directories", numberOfDirectoriesProcessed);
            })
            .toList();

        runAndWaitUntilDone(runnables, threadCount);

        LOG.info("Completed aggregating version-level metrics. Processed {} directories, submitted metrics for {} versions, and skipped metrics for {} versions", numberOfDirectoriesProcessed,
                numberOfVersionsSubmitted, numberOfVersionsSkipped);
    }

    private void aggregateEntryLevelMetrics(List<S3DirectoryInfo> entryDirectories, ExtendedGa4GhApi extendedGa4GhApi, int threadCount) {
        // TODO
    }

    private List<S3DirectoryInfo> calculateEntryDirectories(List<S3DirectoryInfo> versionDirectories) {
        return versionDirectories.stream()
            .map(S3DirectoryInfo::toEntryDirectory)
            .collect(Collectors.groupingBy(S3DirectoryInfo::versionS3KeyPrefix, Collectors.reducing(null, S3DirectoryInfo::combine)))
            .values()
            .stream()
            .toList();
    }

    private void runAndWaitUntilDone(List<Runnable> runnables, int threadCount) {
        // Create an executor with the specified number of threads
        ExecutorService es = Executors.newFixedThreadPool(threadCount);
        // Submit all of the runnables for execution
        runnables.forEach(es::execute);
        // Wait until all of the runnables are done
        es.shutdown();
        try {
            es.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            LOG.info("InterruptedException while waiting for threads to complete");
            es.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Executes the query using AWS Athena and returns a list of QueryResultRow
     * @param query
     * @return
     */
    public List<QueryResultRow> executeQuery(String query) throws AwsServiceException, SdkClientException, InterruptedException {
        List<QueryResultRow> queryResultRows = new ArrayList<>();

        LOG.debug("Running SQL query:\n{}", query);
        GetQueryResultsIterable getQueryResultsIterable = AthenaClientHelper.executeQuery(athenaClient, databaseName, athenaWorkgroup, query);
        Map<String, Integer> columnNameToColumnIndex = new HashMap<>();

        for (GetQueryResultsResponse result : getQueryResultsIterable) {
            // Create a map of column name to column value index. Only needs to be created once because the other pages should have the same columns
            if (columnNameToColumnIndex.isEmpty()) {
                List<ColumnInfo> columnInfoList = result.resultSet().resultSetMetadata().columnInfo();
                for (int i = 0; i < columnInfoList.size(); ++i) {
                    ColumnInfo columnInfo = columnInfoList.get(i);
                    columnNameToColumnIndex.put(columnInfo.name(), i);
                }
            }

            // Get the row values as a list of strings
            List<Row> rows = result.resultSet().rows();
            if (rows.size() > 1) {
                for (Row row : rows.subList(1, rows.size())) { // Ignore first row because it contains column headers
                    LOG.debug("SQL result row: {}\n", row.toString());
                    List<Datum> rowData = row.data();
                    List<String> columnValues = rowData.stream()
                            .map(Datum::varCharValue) // Note: the column value can be null if there's no value for it
                            .toList();
                    queryResultRows.add(new QueryResultRow(columnNameToColumnIndex, columnValues));
                }
            }
        }

        return queryResultRows;
    }

    /**
     * Calculate aggregated metrics for all platforms in the S3 directory.
     * TODO update
     * @return
     */
    public Map<String, Metrics> getAggregatedMetricsForPlatforms(AthenaTablePartition athenaTablePartition, List<String> platforms, String prefix, String name) {
        LOG.info("Aggregating metrics for directory: {}", prefix);
        Map<String, Metrics> platformToMetrics = new HashMap<>();
        try {
            // Calculate metrics for runexecutions
            Map<String, ExecutionStatusMetric> executionStatusMetricByPlatform = executionStatusAggregator.createMetricByPlatform(athenaTablePartition);
            // Calculate metrics for validationexecutions
            Map<String, ValidationStatusMetric> validationStatusMetricByPlatform = validationStatusAggregator.createMetricByPlatform(athenaTablePartition);

            List<String> metricsPlatforms = new ArrayList<>(platforms);
            metricsPlatforms.add(Partner.ALL.name());
            metricsPlatforms.forEach(platform -> {
                ExecutionStatusMetric executionStatusMetric = executionStatusMetricByPlatform.get(platform);
                ValidationStatusMetric validationStatusMetric = validationStatusMetricByPlatform.get(platform);

                if (executionStatusMetric != null || validationStatusMetric != null) {
                    platformToMetrics.putIfAbsent(platform,
                            new Metrics().executionStatusCount(executionStatusMetric).validationStatus(validationStatusMetric));
                    LOG.info("Aggregated metrics for {}, platform {} from directory {}", name, platform, prefix);
                }
            });
        } catch (Exception e) {
            // Log error and continue
            LOG.error("Could not aggregate metrics for {}", name, e);
        }
        return platformToMetrics;
    }

    public void dryRun(List<S3DirectoryInfo> s3DirectoriesToAggregate) {
        LOG.info("These S3 directories will be aggregated:");
        s3DirectoriesToAggregate.forEach(s3Directory -> LOG.info("{}", s3Directory.versionS3KeyPrefix()));
    }

    /**
     * A record that contains information about a single row of results from an Athena query execution.
     * @param columnNameToColumnIndex Map of column names to column index to be used with the columnValues list.
     * @param columnValues List of column values.
     */
    public record QueryResultRow(Map<String, Integer> columnNameToColumnIndex, List<String> columnValues) {
        /**
         * Get the column value that corresponds with the column name.
         * @param columnName
         * @return
         */
        public Optional<String> getColumnValue(String columnName) {
            Integer columnIndex = columnNameToColumnIndex.get(columnName);
            return columnIndex == null ? Optional.empty() : Optional.ofNullable(columnValues.get(columnIndex));
        }

        public Optional<String> getColumnValue(Field<?> field) {
            return getColumnValue(field.getName());
        }
    }

    public record AthenaTablePartition(Optional<String> entity, Optional<String> registry, Optional<String> org, Optional<String> name, Optional<String> version) {
    }
}
