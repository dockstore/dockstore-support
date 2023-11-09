package io.dockstore.metricsaggregator.helper;

import static java.util.stream.Collectors.groupingBy;

import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExecutionStatusAggregator implements Aggregator<ExecutionStatusMetric, ExecutionStatusEnum> {

    @Override
    public ExecutionStatusMetric getMetricFromMetrics(Metrics metrics) {
        return metrics.getExecutionStatusCount();
    }

    @Override
    public ExecutionStatusEnum getMetricFromRunExecution(RunExecution runExecution) {
        return runExecution.getExecutionStatus();
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        if (taskExecutions.stream().map(RunExecution::getExecutionStatus).allMatch(Objects::nonNull)) {
            if (taskExecutions.stream().allMatch(taskRunExecution -> taskRunExecution.getExecutionStatus() == ExecutionStatusEnum.SUCCESSFUL)) {
                // All executions were successful
                return Optional.of(new RunExecution().executionStatus(ExecutionStatusEnum.SUCCESSFUL));
            } else {
                // If there were failed executions, set the overall status to the most frequent failed status
                Optional<ExecutionStatusEnum> mostFrequentFailedStatus = taskExecutions.stream()
                        .map(RunExecution::getExecutionStatus)
                        .filter(taskExecutionStatus -> taskExecutionStatus != ExecutionStatusEnum.SUCCESSFUL)
                        .collect(groupingBy(Function.identity(), Collectors.reducing(0, e -> 1, Integer::sum)))
                        .entrySet()
                        .stream()
                        .max(Entry.comparingByValue())
                        .map(Entry::getKey);
                if (mostFrequentFailedStatus.isPresent()) {
                    return Optional.of(new RunExecution().executionStatus(mostFrequentFailedStatus.get()));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ExecutionStatusMetric> getAggregatedMetricFromWorkflowExecutions(List<RunExecution> workflowExecutions) {
        if (!workflowExecutions.isEmpty()) {
            // Calculate the status count from the workflow executions submitted
            Map<String, Integer> executionsStatusCount = workflowExecutions.stream()
                    .map(execution -> execution.getExecutionStatus().toString())
                    .collect(groupingBy(Function.identity(), Collectors.reducing(0, e -> 1, Integer::sum)));
            return Optional.of(new ExecutionStatusMetric().count(executionsStatusCount));
        }
        return Optional.empty();
    }

    @Override
    public Optional<ExecutionStatusMetric> getAggregatedMetricsFromAggregatedMetrics(List<ExecutionStatusMetric> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            Map<String, Integer> statusCount = aggregatedMetrics.stream()
                    .filter(Objects::nonNull)
                    .map(executionStatusMetric -> executionStatusMetric.getCount().entrySet())
                    .flatMap(Collection::stream)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));

            return statusCount.isEmpty() ? Optional.empty() : Optional.of(new ExecutionStatusMetric().count(statusCount));
        }
        return Optional.empty();
    }
}
