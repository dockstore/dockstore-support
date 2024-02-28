package io.dockstore.metricsaggregator.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionTimeAggregatorTest {
    private static final ExecutionTimeAggregator EXECUTION_TIME_AGGREGATOR = new ExecutionTimeAggregator();

    /**
     * Tests that the aggregator calculates the correct workflow RunExecution from a list of task RunExecutions.
     */
    @Test
    void testGetWorkflowExecutionFromTaskExecutions() {
        // Empty TaskExecutions should return Optional.empty()
        Optional<RunExecution> workflowExecution = EXECUTION_TIME_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(new TaskExecutions());
        assertTrue(workflowExecution.isEmpty());

        // The workflow execution generated from a single task should have the executionTime as the one task
        String tenSecondsExecutionTime = "PT10S";
        TaskExecutions taskExecutions = new TaskExecutions();
        taskExecutions.setTaskExecutions(List.of(createRunExecution(tenSecondsExecutionTime)));
        workflowExecution = EXECUTION_TIME_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(tenSecondsExecutionTime, workflowExecution.get().getExecutionTime());

        // The workflow execution generated from multiple tasks should calculate the executionTime from the earliest and latest dateExecuted fields from the tasks
        List<String> iso8601Dates = List.of("2023-11-09T21:54:00.571285905Z", "2023-11-09T21:54:10.571285905Z", "2023-11-09T21:54:20.571285905Z"); // 10 second increments
        // Create 3 tasks where the difference between each dateExecuted is 10 seconds
        taskExecutions.setTaskExecutions(iso8601Dates.stream()
                .map(dateExecuted -> {
                    // Setting the execution time, but this isn't used to calculate the workflow RunExecution execution time
                    RunExecution taskExecution = createRunExecution(tenSecondsExecutionTime);
                    taskExecution.setDateExecuted(dateExecuted);
                    return taskExecution;
                })
                .toList()
        );
        workflowExecution = EXECUTION_TIME_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        // Should be 30 seconds because the difference between the earliest and latest date executed is 20 seconds and the duration of the last task is 10s
        assertEquals("PT30S", workflowExecution.get().getExecutionTime());
    }

    /**
     * Tests that the aggregator calculates the correct aggregated metric from a list of workflow RunExecutions
     */
    @Test
    void testGetAggregatedMetricFromWorkflowExecutions() {
        // Empty list should return Optional.empty()
        Optional<ExecutionTimeMetric> executionTimeMetric = EXECUTION_TIME_AGGREGATOR.getAggregatedMetricFromExecutions(List.of());
        assertTrue(executionTimeMetric.isEmpty());

        // Test the metric calculated from a single workflow execution. The min, max, and average should be the same value as the single execution
        List<RunExecution> workflowExecutions = List.of(createRunExecution("PT10S")); // 10 seconds
        executionTimeMetric = EXECUTION_TIME_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(executionTimeMetric.isPresent());
        assertEquals(10.0, executionTimeMetric.get().getMinimum());
        assertEquals(10.0, executionTimeMetric.get().getMaximum());
        assertEquals(10.0, executionTimeMetric.get().getAverage());
        assertEquals(1, executionTimeMetric.get().getNumberOfDataPointsForAverage());

        // Test the metric calculated from multiple workflow executions.
        workflowExecutions = List.of(
                createRunExecution("PT10S"), // 10 seconds
                createRunExecution("PT20S"), // 20 seconds
                createRunExecution("PT30S") // 30 seconds
        );
        executionTimeMetric = EXECUTION_TIME_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(executionTimeMetric.isPresent());
        assertEquals(10.0, executionTimeMetric.get().getMinimum());
        assertEquals(30.0, executionTimeMetric.get().getMaximum());
        assertEquals(20.0, executionTimeMetric.get().getAverage());
        assertEquals(3, executionTimeMetric.get().getNumberOfDataPointsForAverage());
    }
    
    private RunExecution createRunExecution(String executionTime) {
        RunExecution runExecution = new RunExecution();
        runExecution.setExecutionTime(executionTime);
        return runExecution;
    }
}
