package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate Memory metrics by calculating the minimum, maximum, and average.
 */
public class MemoryAggregator extends RunExecutionAggregator<MemoryMetric, Double> {

    @Override
    public Double getMetricFromExecution(RunExecution execution) {
        return execution.getMemoryRequirementsGB();
    }

    @Override
    public MemoryMetric getMetricFromMetrics(Metrics metrics) {
        return null;
    }

    @Override
    public boolean validateExecutionMetric(Double executionMetric) {
        return executionMetric >= 0;
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (taskExecutions != null && taskExecutions.stream().map(RunExecution::getMemoryRequirementsGB).allMatch(Objects::nonNull)) {
            // Get the overall memory requirement by getting the maximum memory value used
            final Optional<Double> maxMemoryRequirement = taskExecutions.stream()
                    .map(RunExecution::getMemoryRequirementsGB)
                    .filter(Objects::nonNull)
                    .max(Double::compareTo);
            if (maxMemoryRequirement.isPresent()) {
                return Optional.of(new RunExecution().memoryRequirementsGB(maxMemoryRequirement.get()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<MemoryMetric> getAggregatedMetricFromExecutions(List<RunExecution> executions) {
        List<Double> memoryRequirements = getValidMetricsFromExecutions(executions);
        if (!memoryRequirements.isEmpty()) {
            DoubleStatistics statistics = new DoubleStatistics(memoryRequirements);
            MemoryMetric memoryMetric = new MemoryMetric()
                    .minimum(statistics.getMinimum())
                    .maximum(statistics.getMaximum())
                    .average(statistics.getAverage())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints());
            memoryMetric.setNumberOfSkippedExecutions(calculateNumberOfSkippedExecutions(executions));
            return Optional.of(memoryMetric);
        }
        return Optional.empty();
    }

    @Override
    public Optional<MemoryMetric> getAggregatedMetricsFromAggregatedMetrics(List<MemoryMetric> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            List<DoubleStatistics> statistics = aggregatedMetrics.stream()
                    .map(metric -> new DoubleStatistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage())).toList();
            DoubleStatistics newStatistic = DoubleStatistics.createFromStatistics(statistics);
            MemoryMetric memoryMetric = new MemoryMetric()
                    .minimum(newStatistic.getMinimum())
                    .maximum(newStatistic.getMaximum())
                    .average(newStatistic.getAverage())
                    .numberOfDataPointsForAverage(newStatistic.getNumberOfDataPoints());
            memoryMetric.setNumberOfSkippedExecutions(sumNumberOfSkippedExecutions(aggregatedMetrics));
            return Optional.of(memoryMetric);
        }
        return Optional.empty();
    }
}
