package io.dockstore.metricsaggregator;

import static io.dockstore.metricsaggregator.helper.AthenaClientHelper.createAthenaClient;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;

import io.dockstore.common.Partner;
import io.dockstore.metricsaggregator.MetricsAggregatorS3Client.S3DirectoryInfo;
import io.dockstore.metricsaggregator.helper.AthenaClientHelper;
import io.dockstore.metricsaggregator.helper.ExecutionStatusAthenaAggregator;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.model.EntryTypeMetadata;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RegistryBean;
import io.dockstore.openapi.client.model.SourceControlBean;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final AtomicInteger numberOfDirectoriesProcessed = new AtomicInteger(0);
    private final ExecutionStatusAthenaAggregator executionStatusAggregator = new ExecutionStatusAthenaAggregator();

    private final String metricsBucketName;
    private final String outputS3Bucket;
    private final String databaseName;
    private final String tableName;
    private final AthenaClient athenaClient;
    private final MetadataApi metadataApi;

    public MetricsAggregatorAthenaClient(MetricsAggregatorConfig config) {
        this.metricsBucketName = config.getS3Config().bucket();
        this.outputS3Bucket = config.getAthenaConfig().outputS3Bucket();

        final String underscoredMetricsBucketName = metricsBucketName.replace("-", "_"); // The metrics bucket name is usually in the form of "env-dockstore-metrics-data"
        this.databaseName = underscoredMetricsBucketName + "_database";
        this.tableName = underscoredMetricsBucketName + "_table";
        this.athenaClient = createAthenaClient();
        this.metadataApi = new MetadataApi(setupApiClient(config.getDockstoreConfig().serverUrl())); // Anonymous client

        createDatabase();
        createTable();
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
                LOG.error("No metrics were aggregated");
            }
            platformToMetrics.forEach((platform, metrics) -> {
                //LOG.info("Tool ID: {}, version {}, platform: {}\n{}", s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId(), platform, metrics);
                if (!skipPostingToDockstore) {
                    // TODO: Allow posting to webservice when the Athena prototype is complete
                    extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId());
                }
            });
        });
        LOG.info("Completed aggregating metrics. Processed {} directories", numberOfDirectoriesProcessed);
    }

    public void createDatabase() {
        LOG.info("Creating database: {}", databaseName);
        final String query = String.format("CREATE DATABASE IF NOT EXISTS %s;", databaseName);
        executeQuery(query);
    }

    /**
     * Create a table with a JSON schema and projected partitions, which removes the need to manually manage partitions.
     * @return
     */
    public void createTable() {
        LOG.info("Creating table: {}", tableName);
        final String entityProjectionValues = String.join(",", metadataApi.getEntryTypeMetadataList()
                .stream()
                .map(EntryTypeMetadata::getTerm)
                .toList());
        final String registryProjectionValues = Stream.concat(
                    metadataApi.getSourceControlList().stream().map(SourceControlBean::getValue),
                    metadataApi.getDockerRegistries().stream().map(RegistryBean::getDockerPath))
                .collect(Collectors.joining(","));
        final String platformProjectionValues = String.join(",", Arrays.stream(Partner.values()).map(Partner::name).toList());
        final String query = MessageFormat.format("""
                CREATE EXTERNAL TABLE IF NOT EXISTS {0} (
                    runexecutions array<struct<
                        executionid:string,
                        dateexecuted:string,
                        executionstatus:string,
                        executiontime:string,
                        memoryrequirementsgb:double,
                        cpurequirements:int,
                        cost:struct<value:double,currency:string>,
                        region:string,
                        additionalproperties:string
                        >
                    >,
                    taskexecutions array<struct<
                        executionid:string,
                        taskexecutions:array<struct<
                            executionid:string,
                            dateexecuted:string,
                            executionstatus:string,
                            executiontime:string,
                            memoryrequirements:double,
                            cpurequirements:int,
                            cost:struct<value:double,currency:string>,
                            region:string,
                            additionalproperties:string
                            >
                        >
                    >>,
                    validationexecutions array<struct<
                        executionid:string,
                        dateexecuted:string,
                        validatortool:string,
                        validatortoolversion:string,
                        isvalid:boolean,
                        errormessage:string,
                        additionalproperties:string
                        >
                    >
                )
                PARTITIONED BY (
                    `entity` string,
                    `registry` string,
                    `org` string,
                    `name` string,
                    `version` string,
                    `platform` string
                )
                ROW FORMAT SERDE "org.openx.data.jsonserde.JsonSerDe"
                LOCATION "s3://{1}/"
                TBLPROPERTIES (
                    "projection.enabled" = "true",
                    "projection.entity.type" = "enum",
                    "projection.entity.values" = "{2}",
                    "projection.registry.type" = "enum",
                    "projection.registry.values" = "{3}",
                    "projection.org.type" = "injected",
                    "projection.name.type" = "injected",
                    "projection.version.type" = "injected",
                    "projection.platform.type" = "enum",
                    "projection.platform.values" = "{4}",
                    "storage.location.template" = "s3://{1}/$'{entity}'/$'{registry}'/$'{org}'/$'{name}'/$'{version}'/$'{platform}'/"
                )
                """, tableName, metricsBucketName, entityProjectionValues, registryProjectionValues, platformProjectionValues);
        executeQuery(query);
    }

    public Optional<PartitionExecutionsCount> getPartitionExecutionCount(AthenaTablePartition athenaTablePartition) {
        LOG.info("Getting executions count for partition");
        final String runExecutionsCountColumn = "runexecutionscount";
        final String taskExecutionsCountColumn = "taskexecutionscount";
        final String validationExecutionsCountColumn = "validationexecutionscount";
        final String query = MessageFormat.format("""
                SELECT SUM(cardinality(runexecutions)) AS {0}, SUM(cardinality(taskexecutions)) AS {1}, SUM(cardinality(validationexecutions)) AS {2}
                FROM {3}
                {4}
                """, runExecutionsCountColumn, taskExecutionsCountColumn, validationExecutionsCountColumn, tableName, athenaTablePartition.getWhereCondition());
        List<QueryResultRow> queryResultRows = executeQuery(query);
        if (queryResultRows.isEmpty()) {
            return Optional.empty();
        }
        QueryResultRow countResult = queryResultRows.get(0);
        final int runExecutionsCount = Integer.parseInt(countResult.getColumnValue(runExecutionsCountColumn).orElse("0"));
        final int taskExecutionsCount = Integer.parseInt(countResult.getColumnValue(taskExecutionsCountColumn).orElse("0"));
        final int validationExecutionsCount = Integer.parseInt(countResult.getColumnValue(validationExecutionsCountColumn).orElse("0"));
        return Optional.of(new PartitionExecutionsCount(athenaTablePartition, runExecutionsCount, taskExecutionsCount, validationExecutionsCount));
    }

    /**
     * Creates a view for the runExecutions array.
     * @param athenaTablePartition
     * @return
=     */
    public String createRunExecutionsView(AthenaTablePartition athenaTablePartition) {
        final String viewName = athenaTablePartition.createViewName("runexecutions");
        final String partitionWhereCondition = athenaTablePartition.getWhereCondition();
        LOG.info("Creating runexecutions view: {}", viewName);
        final String query = MessageFormat.format("""
                CREATE OR REPLACE VIEW "{0}" AS
                WITH dataset AS (
                    SELECT platform, runexecutions
                    FROM {1}
                    {2}
                )
                SELECT platform, unnested.executionid, unnested.dateexecuted, unnested.executionstatus, unnested.executiontime, unnested.memoryrequirementsgb, unnested.cpurequirements, unnested.cost, unnested.region
                FROM dataset, UNNEST(dataset.runexecutions) AS t(unnested);
                """, viewName, tableName, partitionWhereCondition);
        executeQuery(query);
        return viewName;
    }

    public String createTaskExecutionsView(AthenaTablePartition athenaTablePartition) {
        final String viewName = athenaTablePartition.createViewName("taskexecutions");
        executeQuery(MessageFormat.format("""
            CREATE OR REPLACE VIEW "{0}" AS
            WITH dataset AS (
                SELECT platform, taskexecutions
                FROM {1}
                {2}
            )
            SELECT platform, unnested.taskexecutions
            FROM dataset, UNNEST(dataset.taskexecutions) AS t(unnested);
            """, viewName, tableName, athenaTablePartition.getWhereCondition()));
        return viewName;
    }

    public String createValidationExecutionsView(AthenaTablePartition athenaTablePartition) {
        final String viewName = athenaTablePartition.createViewName("validationexecutions");
        LOG.info("Creating validationexecutions view: {}", viewName);
        final String query = MessageFormat.format("""
                CREATE OR REPLACE VIEW "{0}" AS
                WITH dataset AS (
                    SELECT platform, validationexecutions
                    FROM {1}
                    {2}
                )
                SELECT platform, unnested.executionid, unnested.dateexecuted, unnested.validatortool, unnested.validatortoolversion, unnested.isvalid, unnested.errormessage
                FROM dataset, UNNEST(dataset.validationexecutions) AS t(unnested);
                """, viewName, tableName, athenaTablePartition.getWhereCondition());
        executeQuery(query);
        return viewName;
    }

    public void dropView(String viewName) {
        final String query = String.format("""
                DROP VIEW "%s";
                """, viewName);
        executeQuery(query);
    }

    /**
     * Executes the query using AWS Athena and returns a list of QueryResultRow
     * @param query
     * @return
     */
    public List<QueryResultRow> executeQuery(String query) {
        List<QueryResultRow> queryResultRows = new ArrayList<>();

        //LOG.info("Running SQL query: {}", query);
        Optional<GetQueryResultsIterable> getQueryResultsIterable = AthenaClientHelper.executeQuery(athenaClient, databaseName, outputS3Bucket, query);
        if (getQueryResultsIterable.isPresent()) {
            Map<String, Integer> columnNameToColumnIndex = new HashMap<>();

            for (GetQueryResultsResponse result : getQueryResultsIterable.get()) {
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
        // Calculate metrics for runexecutions
        // Check if run executions exist before creating view
        // TODO: Calculate metrics for taskexecutions and validationexecutions
        AthenaTablePartition athenaTablePartition = s3DirectoryInfo.athenaTablePartition();
        Optional<PartitionExecutionsCount> partitionExecutionsCount = getPartitionExecutionCount(athenaTablePartition);
        if (partitionExecutionsCount.isEmpty()) {
            LOG.error("Could not get executions count for partition");
            return platformToMetrics;
        }

        LOG.info("Execution count: {}", partitionExecutionsCount.get());

        if (partitionExecutionsCount.get().runExecutionsCount() > 0) {
            String runExecutionsView = createRunExecutionsView(s3DirectoryInfo.athenaTablePartition());
            try {
                if (!runExecutionsView.isEmpty()) {
                    LOG.info("Aggregating workflow executions");
                    String query = executionStatusAggregator.createQuery(runExecutionsView);
                    Map<String, ExecutionStatusMetric> platformToExecutionStatusMetric = executionStatusAggregator.createMetricByPlatform(
                            executeQuery(query));
                    platformToExecutionStatusMetric.forEach((platform, executionStatusMetric) -> {
                        if (platformToMetrics.containsKey(platform)) {
                            platformToMetrics.get(platform).executionStatusCount(executionStatusMetric);
                        } else {
                            platformToMetrics.put(platform, new Metrics().executionStatusCount(executionStatusMetric));
                        }
                        LOG.info("Aggregated metrics for tool ID {}, version {}, platform {} from directory {}", s3DirectoryInfo.toolId(),
                                s3DirectoryInfo.versionId(), platform, s3DirectoryInfo.versionS3KeyPrefix());
                    });
                }
            } catch (Exception e) {
                LOG.error("Could not aggregate metrics for tool ID {}, version {}", s3DirectoryInfo.toolId(), s3DirectoryInfo.versionId(),
                        e);
            } finally {
                // Delete views
                dropView(runExecutionsView);
            }
        }

        numberOfDirectoriesProcessed.incrementAndGet();
        LOG.info("Processed {} directories", numberOfDirectoriesProcessed);
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
            return columnIndex == null || columnValues.get(columnIndex) == null ? Optional.empty() : Optional.of(columnValues.get(columnIndex));
        }
    }

    public record AthenaTablePartition(String entity, String registry, String org, String name, String version) {
        /**
         * Creates a view name. These view names will need to be enclosed with double quotes in queries because the entry components may contain special characters like dashes.
         * @param viewType
         * @return
         */
        public String createViewName(String viewType) {
            return String.join("_", entity, registry, org, name, version, viewType);
        }

        public String getWhereCondition() {
            return String.format("WHERE entity = '%s' AND registry = '%s' AND org = '%s' AND name = '%s' AND version = '%s'", entity, registry, org, name, version);
        }
    }

    public record PartitionExecutionsCount(AthenaTablePartition athenaTablePartition, int runExecutionsCount, int taskExecutionsCount, int validationExecutionsCount) {
    }
}
