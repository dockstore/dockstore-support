package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.openapi.client.model.CostMetric;

/**
 * Aggregate cost metric statistics using AWS Athena.
 */
public class CostAthenaAggregator extends StatisticsAthenaAggregator<CostMetric> {
    public CostAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
    }

    @Override
    String getMetricColumnName() {
        return COST_FIELD.getName() + ".value"; // Only aggregate the cost values
    }

    @Override
    CostMetric createMetricFromStatistics(double min, double avg, double max, double median, double percentile05th, double percentile95th, int numberOfDataPoints) {
        return new CostMetric()
                .minimum(min)
                .average(avg)
                .maximum(max)
                .median(median)
                .percentile05th(percentile05th)
                .percentile95th(percentile95th)
                .numberOfDataPointsForAverage(numberOfDataPoints);
    }
}
