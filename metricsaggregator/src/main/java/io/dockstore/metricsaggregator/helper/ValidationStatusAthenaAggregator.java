package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import java.util.Optional;

/**
 * TODO: Implement the Validation Status Athena aggregator.
 */
public class ValidationStatusAthenaAggregator extends AthenaAggregator<ValidationStatusMetric> {
    @Override
    public String getMetricColumnName() {
        return "*"; // Uses all columns for validationexecutions
    }

    @Override
    public Optional<ValidationStatusMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        return Optional.empty();
    }
}
