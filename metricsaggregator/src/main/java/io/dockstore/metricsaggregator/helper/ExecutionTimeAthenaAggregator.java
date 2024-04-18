package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import java.util.Optional;

/**
 * Aggregate execution time metrics by calculating the min, average, max, and number of data points using AWS Athena.
 * TODO: Implement the executiontime aggregator. Dockstore records executiontime metrics as an ISO-8601 duration which does not translate well in SQL and Athena. We may need to re-evalute how we record this metric.
 */
public class ExecutionTimeAthenaAggregator extends AthenaAggregator<ExecutionTimeMetric> {

    @Override
    public String getMetricColumnName() {
        return "executiontime";
    }

    @Override
    public Optional<ExecutionTimeMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        return Optional.empty();
    }
}
