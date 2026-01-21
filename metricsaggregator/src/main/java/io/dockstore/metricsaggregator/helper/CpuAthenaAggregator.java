package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.openapi.client.model.CpuMetric;
import java.util.Optional;

/**
 * Aggregate CPU metric statistics using AWS Athena.
 */
public class CpuAthenaAggregator extends StatisticsAthenaAggregator<CpuMetric> {
    public CpuAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName) {
        super(metricsAggregatorAthenaClient, tableName);
    }

    @Override
    String getMetricColumnName() {
        return CPU_REQUIREMENTS_FIELD.getName();
    }

    @Override
    CpuMetric createMetricFromStatistics(double min, double avg, double max, double median, double percentile05th, double percentile95th, int numberOfDataPoints) {
        return new CpuMetric()
                .minimum(min)
                .average(avg)
                .maximum(max)
                .median(median)
                .percentile05th(percentile05th)
                .percentile95th(percentile95th)
                .numberOfDataPointsForAverage(numberOfDataPoints);
    }
}
