package io.dockstore.metricsaggregator.helper;

import static io.dockstore.common.metrics.FormatCheckHelper.checkExecutionDateISO8601Format;
import static io.dockstore.common.metrics.FormatCheckHelper.checkExecutionTimeISO8601Format;

import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate Execution Time metrics by calculating the minimum, maximum, and average.
 */
public final class ExecutionTimeAggregator extends RunExecutionAggregator<ExecutionTimeMetric, String> {
    @Override
    public String getMetricFromExecution(RunExecution execution) {
        return execution.getExecutionTime();
    }

    @Override
    public boolean validateExecutionMetric(String executionMetric) {
        return checkExecutionTimeISO8601Format(executionMetric).isPresent();
    }

    @Override
    public String getPropertyPathToValidate() {
        return "executionTime";
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (!taskExecutions.isEmpty() && taskExecutions.stream().map(RunExecution::getExecutionTime).allMatch(Objects::nonNull)) {
            RunExecution workflowExecution = new RunExecution();
            // We cannot calculate the overall total time from RunExecution's executionTime, which is in ISO 8601 duration format.
            // Calculate a best guess using RunExecution's dateExecuted, which is in ISO 8601 date format
            if (taskExecutions.size() == 1 && taskExecutions.get(0).getExecutionTime() != null) {
                // If there's only one task, set the workflow-level execution time to be the execution time of the single task
                workflowExecution.setExecutionTime(taskExecutions.get(0).getExecutionTime());
                return Optional.of(workflowExecution);

            // Calculate a duration if all task executions have a valid dateExecuted
            } else if (taskExecutions.stream().allMatch(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).isPresent())) {
                // Find the earliest date executed and latest date executed to calculate a duration estimate
                final Optional<Date> earliestTaskExecutionDate = taskExecutions.stream()
                        .map(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).get())
                        .min(Date::compareTo);
                final Optional<Date> latestTaskExecutionDate = taskExecutions.stream()
                        .map(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).get())
                        .max(Date::compareTo);
                final Optional<RunExecution> latestTaskExecuted = taskExecutions.stream()
                        .max(Comparator.comparing(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).get(), Date::compareTo));

                if (earliestTaskExecutionDate.isPresent() && latestTaskExecutionDate.isPresent() && latestTaskExecuted.isPresent()) {
                    // Execution dates are the start dates, calculate a rough duration from the execution dates of the earliest and latest tasks
                    long durationInMs = latestTaskExecutionDate.get().getTime() - earliestTaskExecutionDate.get().getTime();
                    Duration duration = Duration.of(durationInMs, ChronoUnit.MILLIS);
                    // If the execution time of the latest task is present, add that to the duration to account for the amount of time the last task took to execute
                    Optional<Duration> latestTaskExecutionTime = checkExecutionTimeISO8601Format(latestTaskExecuted.get().getExecutionTime());
                    if (latestTaskExecutionTime.isPresent()) {
                        duration = duration.plus(latestTaskExecutionTime.get());
                    }
                    workflowExecution.setExecutionTime(duration.toString());
                    return Optional.of(workflowExecution);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    protected Optional<ExecutionTimeMetric> calculateAggregatedMetricFromExecutionMetrics(List<String> executionMetrics) {
        List<Double> executionTimesInSeconds = executionMetrics.stream()
                .map(executionTime -> {
                    // Convert executionTime in ISO 8601 duration format to seconds
                    Duration parsedISO8601ExecutionTime = checkExecutionTimeISO8601Format(executionTime).get();
                    return (double) parsedISO8601ExecutionTime.toSeconds();
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
    protected Optional<ExecutionTimeMetric> calculateAggregatedMetricFromAggregatedMetrics(List<ExecutionTimeMetric> aggregatedMetrics) {
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
