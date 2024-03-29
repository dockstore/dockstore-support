package io.dockstore.metricsaggregator.helper;

import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.MemoryMetric;
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
    public boolean validateExecutionMetric(Double executionMetric) {
        return executionMetric != null && executionMetric >= 0;
    }

    @Override
    public String getPropertyPathToValidate() {
        return "memoryRequirementsGB";
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (!taskExecutions.isEmpty() && taskExecutions.stream().map(RunExecution::getMemoryRequirementsGB).allMatch(Objects::nonNull)) {
            // Get the overall memory requirement by getting the maximum memory value used
            final Optional<Double> maxMemoryRequirement = taskExecutions.stream()
                    .map(RunExecution::getMemoryRequirementsGB)
                    .filter(Objects::nonNull)
                    .max(Double::compareTo);
            if (maxMemoryRequirement.isPresent()) {
                RunExecution workflowExecution = new RunExecution();
                workflowExecution.setMemoryRequirementsGB(maxMemoryRequirement.get());
                return Optional.of(workflowExecution);
            }
        }
        return Optional.empty();
    }

    @Override
    protected Optional<MemoryMetric> calculateAggregatedMetricFromExecutionMetrics(List<Double> executionMetrics) {
        if (!executionMetrics.isEmpty()) {
            DoubleStatistics statistics = new DoubleStatistics(executionMetrics);
            return Optional.of(new MemoryMetric()
                    .minimum(statistics.getMinimum())
                    .maximum(statistics.getMaximum())
                    .average(statistics.getAverage())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }

    @Override
    protected Optional<MemoryMetric> calculateAggregatedMetricFromAggregatedMetrics(List<MemoryMetric> aggregatedMetrics) {
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
