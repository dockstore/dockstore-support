package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
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
public class MemoryAggregator implements ExecutionAggregator<RunExecution, MemoryMetric, Double> {
    @Override
    public MemoryMetric getMetricFromMetrics(Metrics metrics) {
        return null;
    }

    @Override
    public Double getMetricFromExecution(RunExecution execution) {
        return execution.getMemoryRequirementsGB();
    }

    @Override
    public List<RunExecution> getExecutionsFromExecutionRequestBody(ExecutionsRequestBody executionsRequestBody) {
        return executionsRequestBody.getRunExecutions();
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
        List<Double> memoryRequirements = getNonNullMetricsFromExecutions(executions);
        if (!memoryRequirements.isEmpty()) {
            DoubleStatistics statistics = new DoubleStatistics(memoryRequirements);
            return Optional.of(new MemoryMetric()
                    .minimum(statistics.getMinimum())
                    .maximum(statistics.getMaximum())
                    .average(statistics.getAverage())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<MemoryMetric> getAggregatedMetricsFromAggregatedMetrics(List<MemoryMetric> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            List<DoubleStatistics> statistics = aggregatedMetrics.stream()
                    .map(metric -> new DoubleStatistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage())).toList();
            DoubleStatistics newStatistic = DoubleStatistics.createFromStatistics(statistics);
            return Optional.of(new MemoryMetric()
                    .minimum(newStatistic.getMinimum())
                    .maximum(newStatistic.getMaximum())
                    .average(newStatistic.getAverage())
                    .numberOfDataPointsForAverage(newStatistic.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }
}
