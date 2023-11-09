package io.dockstore.metricsaggregator.helper;

import static io.dockstore.common.metrics.FormatCheckHelper.checkExecutionDateISO8601Format;
import static io.dockstore.common.metrics.FormatCheckHelper.checkExecutionTimeISO8601Format;

import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ExecutionTimeAggregator implements Aggregator<ExecutionTimeMetric, String> {
    @Override
    public String getMetricFromRunExecution(RunExecution runExecution) {
        return runExecution.getExecutionTime();
    }

    @Override
    public ExecutionTimeMetric getMetricFromMetrics(Metrics metrics) {
        return metrics.getExecutionTime();
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (taskExecutions.stream().map(RunExecution::getExecutionTime).allMatch(Objects::nonNull)) {
            // We cannot calculate the overall total time from RunExecution's executionTime, which is in ISO 8601 duration format.
            // Calculate a best guess using RunExecution's dateExecuted, which is in ISO 8601 date format
            if (taskExecutions.size() == 1 && taskExecutions.get(0).getExecutionTime() != null) {
                // If there's only one task, set the workflow-level execution time to be the execution time of the single task
                return Optional.of(new RunExecution().executionTime(taskExecutions.get(0).getExecutionTime()));

                // Calculate a duration if all task executions have a valid dateExecuted
            } else if (taskExecutions.stream().allMatch(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).isPresent())) {
                // Find the earliest date executed and latest date executed to calculate a duration estimate
                final Optional<Date> earliestTaskExecutionDate = taskExecutions.stream()
                        .map(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).get())
                        .min(Date::compareTo);
                final Optional<Date> latestTaskExecutionDate = taskExecutions.stream()
                        .map(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).get())
                        .max(Date::compareTo);

                if (earliestTaskExecutionDate.isPresent() && latestTaskExecutionDate.isPresent()) {
                    long durationInMs = latestTaskExecutionDate.get().getTime() - earliestTaskExecutionDate.get().getTime();
                    Duration duration = Duration.of(durationInMs, ChronoUnit.MILLIS);
                    return Optional.of(new RunExecution().executionTime(duration.toString()));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ExecutionTimeMetric> getAggregatedMetricFromWorkflowExecutions(List<RunExecution> workflowExecutions) {
        List<String> executionTimes = getNonNullMetricsFromRunExecutions(workflowExecutions);

        boolean containsMalformedExecutionTimes = executionTimes.stream().anyMatch(executionTime -> checkExecutionTimeISO8601Format(executionTime).isEmpty());
        // This really shouldn't happen because the webservice validates that the ExecutionTime is in the correct format
        if (containsMalformedExecutionTimes) {
            return Optional.empty(); // Don't aggregate if there's malformed data
        }

        List<Double> executionTimesInSeconds = executionTimes.stream()
                .map(executionTime -> {
                    // Convert executionTime in ISO 8601 duration format to seconds
                    Duration parsedISO8601ExecutionTime = checkExecutionTimeISO8601Format(executionTime).get();
                    return Long.valueOf(parsedISO8601ExecutionTime.toSeconds()).doubleValue();
                })
                .toList();

        if (!executionTimesInSeconds.isEmpty()) {
            DoubleStatistics statistics = new DoubleStatistics(executionTimesInSeconds);
            return Optional.of(new ExecutionTimeMetric()
                    .minimum(statistics.getMinimum())
                    .maximum(statistics.getMaximum())
                    .average(statistics.getAverage())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<ExecutionTimeMetric> getAggregatedMetricsFromAggregatedMetrics(List<ExecutionTimeMetric> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            List<DoubleStatistics> statistics = aggregatedMetrics.stream()
                    .map(metric -> new DoubleStatistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage()))
                    .toList();

            DoubleStatistics newStatistic = DoubleStatistics.createFromStatistics(statistics);
            return Optional.of(new ExecutionTimeMetric()
                    .minimum(newStatistic.getMinimum())
                    .maximum(newStatistic.getMaximum())
                    .average(newStatistic.getAverage())
                    .numberOfDataPointsForAverage(newStatistic.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }
}
