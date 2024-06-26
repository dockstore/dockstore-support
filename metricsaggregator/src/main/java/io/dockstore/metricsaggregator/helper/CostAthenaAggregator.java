package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.CostMetric;
import java.util.Optional;

/**
 * Aggregate cost metrics by calculating the min, average, max, and number of data points using AWS Athena.
 */
public class CostAthenaAggregator extends RunExecutionAthenaAggregator<CostMetric> {
    public CostAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
        this.addSelectFields(getStatisticSelectFields());
    }

    @Override
    String getMetricColumnName() {
        return COST_FIELD.getName() + ".value"; // Only aggregate the cost values
    }

    @Override
    Optional<CostMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        Optional<Double> min = getMinColumnValue(queryResultRow);
        Optional<Double> avg = getAvgColumnValue(queryResultRow);
        Optional<Double> max = getMaxColumnValue(queryResultRow);
        Optional<Integer> numberOfDataPoints = getCountColumnValue(queryResultRow);
        if (min.isPresent() && avg.isPresent() && max.isPresent() && numberOfDataPoints.isPresent()) {
            return Optional.of(new CostMetric()
                    .minimum(min.get())
                    .average(avg.get())
                    .maximum(max.get())
                    .numberOfDataPointsForAverage(numberOfDataPoints.get()));
        }
        return Optional.empty();
    }
}
