package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.field;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.AthenaTablePartition;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.Metric;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Field;

/**
 * An abstract class that helps create SQL statements to aggregate metrics that are executed by AWS Athena.
 * Utilizes the JOOQ library to construct dynamic SQL statements.
 */
public abstract class AthenaAggregator<M extends Metric> {
    protected static final Field<String> ENTITY_FIELD = field("entity", String.class);
    protected static final Field<String> REGISTRY_FIELD = field("registry", String.class);
    protected static final Field<String> ORG_FIELD = field("org", String.class);
    protected static final Field<String> NAME_FIELD = field("name", String.class);
    protected static final Field<String> VERSION_FIELD = field("version", String.class);
    protected static final Field<String> PLATFORM_FIELD = field("platform", String.class);

    /**
     * Create the query to aggregate the metrics.
     */
    public abstract String createQuery(String tableName, AthenaTablePartition partition);

    /**
     * Given a list of query result rows, creates a metric for each row and maps it to a platform
     * @param queryResultRows
     * @return
     */
    public abstract Map<String, M> createMetricByPlatform(List<QueryResultRow> queryResultRows);

    /**
     * Get the platform column value from the query result row
     * @param queryResultRow
     * @return
     */
    public Optional<String> getPlatformFromQueryResultRow(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(PLATFORM_FIELD.getName());
    }
}
