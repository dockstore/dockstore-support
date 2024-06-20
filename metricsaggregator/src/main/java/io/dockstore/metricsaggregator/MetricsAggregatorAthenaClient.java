package io.dockstore.metricsaggregator;

import static io.dockstore.metricsaggregator.helper.AthenaClientHelper.createAthenaClient;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final String outputS3Bucket;
    private final String databaseName;
    private final String tableName;
    private final AthenaClient athenaClient;
    private final MetadataApi metadataApi;

    private final AtomicInteger numberOfDirectoriesProcessed = new AtomicInteger(0);
    private final AtomicInteger numberOfMetricsSubmitted = new AtomicInteger(0);
    private final AtomicInteger numberOfMetricsSkipped = new AtomicInteger(0);

    public MetricsAggregatorAthenaClient(MetricsAggregatorConfig config) {
        this.metricsBucketName = config.getS3Config().bucket();
        this.outputS3Bucket = config.getAthenaConfig().outputS3Bucket();

        final String underscoredMetricsBucketName = metricsBucketName.replace("-", "_"); // The metrics bucket name is usually in the form of "env-dockstore-metrics-data"
        this.databaseName = underscoredMetricsBucketName + "_database";
        this.tableName = underscoredMetricsBucketName + "_table";
        this.athenaClient = createAthenaClient();
        this.metadataApi = new MetadataApi(setupApiClient(config.getDockstoreConfig().serverUrl())); // Anonymous client
        this.executionStatusAggregator = new ExecutionStatusAthenaAggregator(this, tableName);
        this.validationStatusAggregator = new ValidationStatusAthenaAggregator(this, tableName);

        AthenaAggregator.createDatabase(databaseName, this);
        AthenaAggregator.createTable(tableName, metricsBucketName, metadataApi, this);
    }

    /**
     * Aggregate metrics using AWS Athena for the list of S3 directories and posts them to Dockstore.
     * @param s3DirectoriesToAggregate
     * @param extendedGa4GhApi
     * @param skipPostingToDockstore
     */
    public void aggregateMetrics(List<S3DirectoryInfo> s3DirectoriesToAggregate, ExtendedGa4GhApi extendedGa4GhApi, boolean skipPostingToDockstore) {
        // Aggregate metrics for each directory
        s3DirectoriesToAggregate.stream().parallel().forEach(s3DirectoryInfo -> {
            Map<String, Metrics> platformToMetrics = getAggregatedMetricsForPlatforms(s3DirectoryInfo);
            if (platformToMetrics.isEmpty()) {
                LOG.error("No metrics were aggregated for tool ID: {}, version {}", s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId());
                numberOfMetricsSkipped.incrementAndGet();
            }
            platformToMetrics.forEach((platform, metrics) -> {
                if (!skipPostingToDockstore) {
                    try {
                        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId());
                        LOG.info("Posted aggregated metrics to Dockstore for tool ID: {}, version {}, platform: {}",
                                s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId(), platform);
                        numberOfMetricsSubmitted.incrementAndGet();
                    } catch (ApiException exception) {
                        // Log error and continue processing for other platforms
                        LOG.error("Could not post aggregated metrics to Dockstore for tool ID: {}, version {}, platform: {}", s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId(), platform);
                        numberOfMetricsSkipped.incrementAndGet();
                    }
                }
            });
            numberOfDirectoriesProcessed.incrementAndGet();
            LOG.info("Processed {} directories", numberOfDirectoriesProcessed);
        });
        LOG.info("Completed aggregating metrics. Processed {} directories, submitted {} platform metrics, and skipped {} platform metrics", numberOfDirectoriesProcessed, numberOfMetricsSubmitted, numberOfMetricsSkipped);
    }

    /**
     * Executes the query using AWS Athena and returns a list of QueryResultRow
     * @param query
     * @return
     */
    public List<QueryResultRow> executeQuery(String query) throws Exception {
        List<QueryResultRow> queryResultRows = new ArrayList<>();

        //LOG.info("Running SQL query:\n{}", query);
        GetQueryResultsIterable getQueryResultsIterable = AthenaClientHelper.executeQuery(athenaClient, databaseName, outputS3Bucket, query);
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
     * @param s3DirectoryInfo
     * @return
     */
    public Map<String, Metrics> getAggregatedMetricsForPlatforms(S3DirectoryInfo s3DirectoryInfo) {
        LOG.info("Aggregating metrics for directory: {}", s3DirectoryInfo.versionS3KeyPrefix());
        Map<String, Metrics> platformToMetrics = new HashMap<>();
        AthenaTablePartition athenaTablePartition = s3DirectoryInfo.athenaTablePartition();
        try {
            // Calculate metrics for runexecutions
            Map<String, ExecutionStatusMetric> executionStatusMetricByPlatform = executionStatusAggregator.createMetricByPlatform(athenaTablePartition);
            // Calculate metrics for validationexecutions
            Map<String, ValidationStatusMetric> validationStatusMetricByPlatform = validationStatusAggregator.createMetricByPlatform(athenaTablePartition);

            s3DirectoryInfo.platforms().forEach(platform -> {
                ExecutionStatusMetric executionStatusMetric = executionStatusMetricByPlatform.get(platform);
                ValidationStatusMetric validationStatusMetric = validationStatusMetricByPlatform.get(platform);

                if (executionStatusMetric != null || validationStatusMetric != null) {
                    platformToMetrics.putIfAbsent(platform, new Metrics().executionStatusCount(executionStatusMetric).validationStatus(validationStatusMetric));
                    LOG.info("Aggregated metrics for tool ID {}, version {}, platform {} from directory {}", s3DirectoryInfo.toolId(),
                            s3DirectoryInfo.versionId(), platform, s3DirectoryInfo.versionS3KeyPrefix());
                }
            });
        } catch (Exception e) {
            // Log error and continue
            LOG.error("Could not aggregate metrics for tool ID {}, version {}", s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId(), e);
        }
        return platformToMetrics;
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

    public record AthenaTablePartition(String entity, String registry, String org, String name, String version) {
    }
}
