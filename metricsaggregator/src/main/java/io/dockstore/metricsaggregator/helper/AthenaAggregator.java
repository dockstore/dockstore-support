package io.dockstore.metricsaggregator.helper;

import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.partitionBy;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.unnest;

import io.dockstore.common.Partner;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.AthenaTablePartition;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.model.EntryTypeMetadata;
import io.dockstore.openapi.client.model.Metric;
import io.dockstore.openapi.client.model.RegistryBean;
import io.dockstore.openapi.client.model.SourceControlBean;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * An abstract class that helps create SQL statements to aggregate metrics that are executed by AWS Athena.
 * Utilizes the JOOQ library to construct dynamic SQL statements.
 */
public abstract class AthenaAggregator<M extends Metric> {
    protected static final Field<String> DATE_EXECUTED_FIELD = field("dateexecuted", String.class);
    // Partition fields
    protected static final Field<String> ENTITY_FIELD = field("entity", String.class);
    protected static final Field<String> REGISTRY_FIELD = field("registry", String.class);
    protected static final Field<String> ORG_FIELD = field("org", String.class);
    protected static final Field<String> NAME_FIELD = field("name", String.class);
    protected static final Field<String> VERSION_FIELD = field("version", String.class);
    protected static final Field<String> PLATFORM_FIELD = field("platform", String.class);

    // S3 metadata fields
    protected static final Field<String> FILE_MODIFIED_TIME_FIELD = field("\"$file_modified_time\"", String.class);
    protected static final Field<Integer> FILE_MODIFIED_TIME_ROW_NUM_FIELD = field("filemodifiedtimerownum", Integer.class);

    private static final Logger LOG = LoggerFactory.getLogger(AthenaAggregator.class);

    protected MetricsAggregatorAthenaClient metricsAggregatorAthenaClient;
    protected String tableName;

    protected AthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        this.metricsAggregatorAthenaClient = metricsAggregatorAthenaClient;
        this.tableName = tableName;
    }

    /**
     * Create the query to aggregate the metrics.
     */
    protected abstract String createQuery(AthenaTablePartition partition);

    /**
     * Given a list of query result rows, creates a metric for each row and maps it to a platform
     * @param queryResultRows
     * @return
     */
    protected abstract Map<String, M> createMetricByPlatform(List<QueryResultRow> queryResultRows);

    public Map<String, M> createMetricByPlatform(AthenaTablePartition partition) {
        List<QueryResultRow> queryResultRows;
        try {
            queryResultRows = metricsAggregatorAthenaClient.executeQuery(createQuery(partition));
        } catch (AwsServiceException | SdkClientException | InterruptedException e) {
            LOG.error("Could not execute query for partition {}", partition, e);
            return Map.of();
        }

        Map<String, M> metricByPlatform = createMetricByPlatform(queryResultRows);
        // Check that metrics exist for actual platforms. May end up with metrics for only 'ALL' if there are no executions of that type
        // because null platform values are coalesced to 'ALL'.
        if (metricByPlatform.size() == 1 && metricByPlatform.containsKey(Partner.ALL.name())) {
            return Map.of();
        }
        return metricByPlatform;
    }

    public void printQuery(AthenaTablePartition partition) {
        LOG.info(createQuery(partition));
    }

    /**
     * Get the platform column value from the query result row
     * @param queryResultRow
     * @return
     */
    protected Optional<String> getPlatformFromQueryResultRow(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(PLATFORM_FIELD);
    }

    /**
     * Creates the Athena database if it doesn't exist.
     * @param databaseName
     * @param metricsAggregatorAthenaClient
     * @throws Exception
     */
    public static void createDatabase(String databaseName, MetricsAggregatorAthenaClient metricsAggregatorAthenaClient) {
        LOG.info("Creating database: {}", databaseName);
        final String query = String.format("CREATE DATABASE IF NOT EXISTS %s;", databaseName);
        try {
            metricsAggregatorAthenaClient.executeQuery(query);
        } catch (AwsServiceException | SdkClientException | InterruptedException e) {
            exceptionMessage(e, "Could not execute query to create Athena database", GENERIC_ERROR);
        }
    }

    /**
     * Creates an Athena table with a JSON schema and projected partitions, which removes the need to manually manage partitions.
     * Drops the table first before creating it in case there are schema changes.
     * https://docs.aws.amazon.com/athena/latest/ug/create-table.html#synopsis
     * @return
     */
    public static void createTable(String tableName, String metricsBucketName, MetadataApi metadataApi, MetricsAggregatorAthenaClient metricsAggregatorAthenaClient) {
        LOG.info("Dropping table: {}", tableName);
        try {
            metricsAggregatorAthenaClient.executeQuery(String.format("DROP TABLE IF EXISTS %s;", tableName));
        } catch (AwsServiceException | SdkClientException | InterruptedException e) {
            exceptionMessage(e, "Could not execute query to drop Athena table", GENERIC_ERROR);
        }

        LOG.info("Creating table: {}", tableName);
        // The table uses partition projection to speed up query processing of partitioned tables and automate partition management
        // https://docs.aws.amazon.com/athena/latest/ug/partition-projection.html
        // Below, we are specifying the ranges of values for partitions that have a predefined set of values,
        // such as entity types (workflow, tool, etc.), registries (github.com, etc.), and platforms (terra, etc.)
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
                        executiontimeseconds:integer,
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
                            executiontimeseconds:integer,
                            memoryrequirementsgb:double,
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

        try {
            metricsAggregatorAthenaClient.executeQuery(query);
        } catch (AwsServiceException | SdkClientException | InterruptedException e) {
            exceptionMessage(e, "Could not execute query to create Athena table", GENERIC_ERROR);
        }
    }

    /**
     * Creates a query that unnests an executions array field and de-duplicates the executions if they have the same execution ID, taking the most recent execution.
     * We have to do this because the webservice does not check for duplicate execution IDs.
     * @param partition
     * @param fieldToUnnest
     * @param fieldsToSelectInUnnestField
     * @return
     */
    protected SelectConditionStep<Record> createUnnestQueryWithModifiedTime(AthenaTablePartition partition, Field<?> fieldToUnnest, List<Field<?>> fieldsToSelectInUnnestField) {
        final String unnestedFieldAlias = "unnestedexecution";
        final Field<String> unnestedExecutionId = field(unnestedFieldAlias + ".executionid", String.class);
        final Select<?> unnestedExecutionsWithFileModifiedTime = select(FILE_MODIFIED_TIME_FIELD,
                rowNumber().over(partitionBy(PLATFORM_FIELD, unnestedExecutionId).orderBy(FILE_MODIFIED_TIME_FIELD.desc())).as(FILE_MODIFIED_TIME_ROW_NUM_FIELD),
                PLATFORM_FIELD,
                field(unnestedFieldAlias, String.class))
                .from(table(tableName), unnest(fieldToUnnest).as("t", unnestedFieldAlias))
                .where(createPartitionSelector(partition));

        List<? extends Field<?>> unnestedFields = fieldsToSelectInUnnestField.stream()
                .map(field -> field(unnestedFieldAlias + "." + field.getName(), field.getType()))
                .toList();

        List<Field<?>> fieldsWithPlatform = new ArrayList<>();
        fieldsWithPlatform.add(PLATFORM_FIELD);
        fieldsWithPlatform.addAll(unnestedFields);

        return select(fieldsWithPlatform)
                .from(unnestedExecutionsWithFileModifiedTime)
                .where(FILE_MODIFIED_TIME_ROW_NUM_FIELD.eq(inline(1)));
    }

    private Condition createPartitionSelector(AthenaTablePartition partition) {
        return createFieldSelector(ENTITY_FIELD, partition.entity())
            .and(createFieldSelector(REGISTRY_FIELD, partition.registry()))
            .and(createFieldSelector(ORG_FIELD, partition.org()))
            .and(createFieldSelector(NAME_FIELD, partition.name()))
            .and(createFieldSelector(VERSION_FIELD, partition.version()));
    }

    private Condition createFieldSelector(Field<String> field, Set<String> values) {
        if (values.size() == 1) {
            return field.eq(inline(values.iterator().next()));
        } else {
            return field.in(values.stream().map(DSL::inline).toList());
        }
    }
}
