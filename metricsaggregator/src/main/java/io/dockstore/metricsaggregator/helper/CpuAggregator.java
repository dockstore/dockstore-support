package io.dockstore.metricsaggregator.helper;

import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.CpuMetric;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate CPU metrics by calculating the minimum, maximum, and average.
 * @return
 */
public class CpuAggregator extends RunExecutionAggregator<CpuMetric, Integer> {

    @Override
    public Integer getMetricFromExecution(RunExecution execution) {
        return execution.getCpuRequirements();
    }

    @Override
    public boolean validateExecutionMetric(Integer executionMetric) {
        return executionMetric != null && executionMetric >= 0;
    }

    @Override
    public String getPropertyPathToValidate() {
        return "cpuRequirements";
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (!taskExecutions.isEmpty() && taskExecutions.stream().map(RunExecution::getCpuRequirements).allMatch(Objects::nonNull)) {
            // Get the overall CPU requirement by getting the maximum CPU value used
            final Optional<Integer> maxCpuRequirement = taskExecutions.stream()
                    .map(RunExecution::getCpuRequirements)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo);
            if (maxCpuRequirement.isPresent()) {
                RunExecution workflowExecution = new RunExecution();
                workflowExecution.setCpuRequirements(maxCpuRequirement.get());
                return Optional.ofNullable(workflowExecution);
            }
        }
        return Optional.empty();
    }

    @Override
    protected Optional<CpuMetric> calculateAggregatedMetricFromExecutionMetrics(List<Integer> executionMetrics) {
        List<Double> cpuRequirements = executionMetrics.stream()
                .map(Integer::doubleValue)
                .toList();
        if (!cpuRequirements.isEmpty()) {
            DoubleStatistics statistics = new DoubleStatistics(cpuRequirements);
            return Optional.of(new CpuMetric()
                    .minimum(statistics.getMinimum())
                    .maximum(statistics.getMaximum())
                    .average(statistics.getAverage())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }

    @Override
    protected Optional<CpuMetric> calculateAggregatedMetricFromAggregatedMetrics(List<CpuMetric> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            List<DoubleStatistics> statistics = aggregatedMetrics.stream()
                    .map(metric -> new DoubleStatistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage())).toList();
            DoubleStatistics newStatistic = DoubleStatistics.createFromStatistics(statistics);
            return Optional.of(new CpuMetric()
                    .minimum(newStatistic.getMinimum())
                    .maximum(newStatistic.getMaximum())
                    .average(newStatistic.getAverage())
                    .numberOfDataPointsForAverage(newStatistic.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }
}
