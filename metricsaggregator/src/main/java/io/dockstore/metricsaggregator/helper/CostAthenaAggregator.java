package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.CostMetric;
import java.util.Optional;

/**
 * Aggregate cost metrics by calculating the min, average, max, and number of data points using AWS Athena.
 */
public class CostAthenaAggregator extends AthenaAggregator<CostMetric> {
    public CostAthenaAggregator() {
        super();
        this.addSelectFields(getStatisticSelectFields());
    }

    @Override
    public String getMetricColumnName() {
        return "cost.value";
    }

    @Override
    public Optional<CostMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
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
