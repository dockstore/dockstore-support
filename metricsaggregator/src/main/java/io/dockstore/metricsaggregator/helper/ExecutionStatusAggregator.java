package io.dockstore.metricsaggregator.helper;

import static java.util.stream.Collectors.groupingBy;

import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.MetricsByStatus;
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

/**
 * Aggregate Execution Status metrics by summing up the count of each Execution Status.
 */
public class ExecutionStatusAggregator extends RunExecutionAggregator<ExecutionStatusMetric, ExecutionStatusEnum> {
    // Aggregators used to calculate metrics by execution status
    private final ExecutionTimeAggregator executionTimeAggregator = new ExecutionTimeAggregator();
    private final CpuAggregator cpuAggregator = new CpuAggregator();
    private final MemoryAggregator memoryAggregator = new MemoryAggregator();
    private final CostAggregator costAggregator = new CostAggregator();

    @Override
    public ExecutionStatusMetric getMetricFromMetrics(Metrics metrics) {
        return metrics.getExecutionStatusCount();
    }

    @Override
    public boolean validateExecutionMetric(ExecutionStatusEnum executionMetric) {
        return true; // Will always be valid if it's an enum
    }

    @Override
    public ExecutionStatusEnum getMetricFromExecution(RunExecution execution) {
        return execution.getExecutionStatus();
    }

    @Override
    public Optional<RunExecution> getWorkflowExecutionFromTaskExecutions(TaskExecutions taskExecutionsForOneWorkflowRun) {
        final List<RunExecution> taskExecutions = taskExecutionsForOneWorkflowRun.getTaskExecutions();
        RunExecution workflowExecution = new RunExecution();
        if (taskExecutions != null && taskExecutions.stream().map(RunExecution::getExecutionStatus).allMatch(Objects::nonNull)) {
            if (taskExecutions.stream().allMatch(taskRunExecution -> taskRunExecution.getExecutionStatus() == ExecutionStatusEnum.SUCCESSFUL)) {
                // All executions were successful
                workflowExecution.setExecutionStatus(ExecutionStatusEnum.SUCCESSFUL);
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
                mostFrequentFailedStatus.ifPresent(workflowExecution::setExecutionStatus);
            }

            if (workflowExecution.getExecutionStatus() != null) {
                executionTimeAggregator.getWorkflowExecutionFromTaskExecutions(taskExecutionsForOneWorkflowRun).ifPresent(executionWithTime -> workflowExecution.setExecutionTime(executionWithTime.getExecutionTime()));
                cpuAggregator.getWorkflowExecutionFromTaskExecutions(taskExecutionsForOneWorkflowRun).ifPresent(executionWithCpu -> workflowExecution.setCpuRequirements(executionWithCpu.getCpuRequirements()));
                memoryAggregator.getWorkflowExecutionFromTaskExecutions(taskExecutionsForOneWorkflowRun).ifPresent(executionWithMemory -> workflowExecution.setMemoryRequirementsGB(executionWithMemory.getMemoryRequirementsGB()));
                costAggregator.getWorkflowExecutionFromTaskExecutions(taskExecutionsForOneWorkflowRun).ifPresent(executionWithCost -> workflowExecution.setCost(executionWithCost.getCost()));
                return Optional.of(workflowExecution);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ExecutionStatusMetric> getAggregatedMetricFromExecutions(List<RunExecution> executions) {
        if (!executions.isEmpty()) {
            Map<ExecutionStatusEnum, List<RunExecution>> executionsByStatus = executions.stream()
                    .collect(groupingBy(RunExecution::getExecutionStatus));

            ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric();
            executionsByStatus.forEach((status, executionsForStatus) -> {
                MetricsByStatus metricsByStatus = getMetricsByStatusFromExecutions(executionsForStatus);
                executionStatusMetric.getCount().put(status.toString(), metricsByStatus);
            });

            // Figure out metrics over all statuses
            MetricsByStatus overallMetricsByStatus = getMetricsByStatusFromExecutions(executionsByStatus.values().stream().flatMap(Collection::stream).toList());
            executionStatusMetric.getCount().put(ExecutionStatusEnum.ALL.name(), overallMetricsByStatus);
            executionStatusMetric.setNumberOfSkippedExecutions(calculateNumberOfSkippedExecutions(executions));

            return Optional.of(executionStatusMetric);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ExecutionStatusMetric> getAggregatedMetricsFromAggregatedMetrics(List<ExecutionStatusMetric> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            Map<String, List<MetricsByStatus>> statusToMetricsByStatus = aggregatedMetrics.stream()
                    .filter(Objects::nonNull)
                    .map(executionStatusMetric -> executionStatusMetric.getCount().entrySet())
                    .flatMap(Collection::stream)
                    .collect(groupingBy(Map.Entry::getKey, Collectors.mapping(Entry::getValue, Collectors.toList())));

            if (statusToMetricsByStatus.isEmpty()) {
                return Optional.empty();
            }

            ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric();
            statusToMetricsByStatus.forEach((status, metricsForStatus) -> {
                MetricsByStatus metricsByStatus = getMetricsByStatusFromMetricsByStatusList(metricsForStatus);
                executionStatusMetric.getCount().put(status, metricsByStatus);
            });

            // Calculate metrics over all statuses
            // Calculate from previous ALL MetricsByStatus (aggregate using aggregated data)
            List<MetricsByStatus> metricsByStatusesToCalculateAllStatus = statusToMetricsByStatus.get(ExecutionStatusEnum.ALL.name());
            if (metricsByStatusesToCalculateAllStatus == null) {
                // If there's no ALL key, calculate from other statuses
                metricsByStatusesToCalculateAllStatus = statusToMetricsByStatus.values().stream().flatMap(Collection::stream).toList();
            }
            MetricsByStatus overallMetricsByStatus = getMetricsByStatusFromMetricsByStatusList(metricsByStatusesToCalculateAllStatus);
            executionStatusMetric.getCount().put(ExecutionStatusEnum.ALL.name(), overallMetricsByStatus);
            executionStatusMetric.setNumberOfSkippedExecutions(sumNumberOfSkippedExecutions(aggregatedMetrics));
            return Optional.of(executionStatusMetric);
        }
        return Optional.empty();
    }

    /**
     * Aggregate executions into a MetricsByStatus object. Assumes that all executions have the status
     * @param executions
     * @return
     */
    private MetricsByStatus getMetricsByStatusFromExecutions(List<RunExecution> executions) {
        MetricsByStatus metricsByStatus = new MetricsByStatus()
                .executionStatusCount(executions.size());

        // Figure out metrics by status
        executionTimeAggregator.getAggregatedMetricFromExecutions(executions).ifPresent(metricsByStatus::setExecutionTime);
        cpuAggregator.getAggregatedMetricFromExecutions(executions).ifPresent(metricsByStatus::setCpu);
        memoryAggregator.getAggregatedMetricFromExecutions(executions).ifPresent(metricsByStatus::setMemory);
        costAggregator.getAggregatedMetricFromExecutions(executions).ifPresent(metricsByStatus::setCost);
        return metricsByStatus;
    }

    /**
     * Aggregate a list of MetricsByStatus objects into a MetricsByStatus object. Assumes that all executions have the same status
     * @param metricsByStatuses
     * @return
     */
    private MetricsByStatus getMetricsByStatusFromMetricsByStatusList(List<MetricsByStatus> metricsByStatuses) {
        final int totalCountForStatus = metricsByStatuses.stream().map(MetricsByStatus::getExecutionStatusCount).reduce(0, Integer::sum);
        MetricsByStatus metricsByStatus = new MetricsByStatus()
                .executionStatusCount(totalCountForStatus);

        // Figure out metrics by status
        executionTimeAggregator.getAggregatedMetricsFromAggregatedMetrics(metricsByStatuses.stream().map(MetricsByStatus::getExecutionTime).filter(Objects::nonNull).toList()).ifPresent(metricsByStatus::setExecutionTime);
        cpuAggregator.getAggregatedMetricsFromAggregatedMetrics(metricsByStatuses.stream().map(MetricsByStatus::getCpu).filter(Objects::nonNull).toList()).ifPresent(metricsByStatus::setCpu);
        memoryAggregator.getAggregatedMetricsFromAggregatedMetrics(metricsByStatuses.stream().map(MetricsByStatus::getMemory).filter(Objects::nonNull).toList()).ifPresent(metricsByStatus::setMemory);
        costAggregator.getAggregatedMetricsFromAggregatedMetrics(metricsByStatuses.stream().map(MetricsByStatus::getCost).filter(Objects::nonNull).toList()).ifPresent(metricsByStatus::setCost);
        return metricsByStatus;
    }
}
