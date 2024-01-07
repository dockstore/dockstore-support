package io.dockstore.metricsaggregator.helper;

import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AggregationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationHelper.class);

    private AggregationHelper() {
    }

    /**
     * Aggregate metrics from all submissions.
     *
     * @param allSubmissions
     * @return Metrics object containing aggregated metrics
     */
    public static Optional<Metrics> getAggregatedMetrics(ExecutionsRequestBody allSubmissions) {
        Metrics aggregatedMetrics = new Metrics();
        // Set run metrics
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = new ExecutionStatusAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions);
        boolean containsRunMetrics = aggregatedExecutionStatus.isPresent();
        if (aggregatedExecutionStatus.isPresent()) {
            aggregatedMetrics.setExecutionStatusCount(aggregatedExecutionStatus.get());
            new ExecutionTimeAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions).ifPresent(aggregatedMetrics::setExecutionTime);
            new CpuAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions).ifPresent(aggregatedMetrics::setCpu);
            new MemoryAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions).ifPresent(aggregatedMetrics::setMemory);
            new CostAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions).ifPresent(aggregatedMetrics::setCost);
        }

        // Set validation metrics
        Optional<ValidationStatusMetric> aggregatedValidationStatus = new ValidationStatusAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions);
        boolean containsValidationMetrics = aggregatedValidationStatus.isPresent();
        aggregatedValidationStatus.ifPresent(aggregatedMetrics::setValidationStatus);

        // Only return aggregated metrics if it contains either run metrics or validation metrics
        if (containsRunMetrics || containsValidationMetrics) {
            return Optional.of(aggregatedMetrics);
        }

        return Optional.empty();
    }

    /**
     * Aggregates metrics into a single metric
     * @param aggregatedMetrics
     * @return
     */
    public static Optional<Metrics> getAggregatedMetrics(List<Metrics> aggregatedMetrics) {
        Metrics overallMetrics = new Metrics();
        // Set run metrics
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = new ExecutionStatusAggregator().getAggregatedMetricFromMetricsList(aggregatedMetrics);
        boolean containsRunMetrics = aggregatedExecutionStatus.isPresent();
        if (aggregatedExecutionStatus.isPresent()) {
            overallMetrics.setExecutionStatusCount(aggregatedExecutionStatus.get());
            new ExecutionTimeAggregator().getAggregatedMetricFromMetricsList(aggregatedMetrics).ifPresent(overallMetrics::setExecutionTime);
            new CpuAggregator().getAggregatedMetricFromMetricsList(aggregatedMetrics).ifPresent(overallMetrics::setCpu);
            new MemoryAggregator().getAggregatedMetricFromMetricsList(aggregatedMetrics).ifPresent(overallMetrics::setMemory);
            new CostAggregator().getAggregatedMetricFromMetricsList(aggregatedMetrics).ifPresent(overallMetrics::setCost);
        }

        // Set validation metrics
        Optional<ValidationStatusMetric> aggregatedValidationStatus = new ValidationStatusAggregator().getAggregatedMetricFromMetricsList(aggregatedMetrics);
        boolean containsValidationMetrics = aggregatedValidationStatus.isPresent();
        aggregatedValidationStatus.ifPresent(overallMetrics::setValidationStatus);

        // Only return aggregated metrics if it contains either run metrics or validation metrics
        if (containsRunMetrics || containsValidationMetrics) {
            return Optional.of(overallMetrics);
        }

        return Optional.empty();
    }
}
