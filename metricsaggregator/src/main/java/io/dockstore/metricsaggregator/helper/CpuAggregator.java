package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CpuAggregator implements Aggregator<CpuMetric, Integer> {
    @Override
    public CpuMetric getMetricFromMetrics(Metrics metrics) {
        return metrics.getCpu();
    }

    @Override
    public Integer getMetricFromRunExecution(RunExecution runExecution) {
        return runExecution.getCpuRequirements();
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (taskExecutions.stream().map(RunExecution::getCpuRequirements).allMatch(Objects::nonNull)) {
            // Get the overall CPU requirement by getting the maximum CPU value used
            final Optional<Integer> maxCpuRequirement = taskExecutions.stream()
                    .map(RunExecution::getCpuRequirements)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo);
            if (maxCpuRequirement.isPresent()) {
                return Optional.ofNullable(new RunExecution().cpuRequirements(maxCpuRequirement.get()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<CpuMetric> getAggregatedMetricFromWorkflowExecutions(List<RunExecution> workflowExecutions) {
        List<Double> cpuRequirements = getNonNullMetricsFromRunExecutions(workflowExecutions).stream()
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
    public Optional<CpuMetric> getAggregatedMetricsFromAggregatedMetrics(List<CpuMetric> aggregatedMetrics) {
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
