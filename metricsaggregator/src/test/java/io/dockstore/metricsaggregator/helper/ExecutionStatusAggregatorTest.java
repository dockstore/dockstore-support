package io.dockstore.metricsaggregator.helper;

import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_RUNTIME_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_SEMANTIC_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionStatusAggregatorTest {
    private static final ExecutionStatusAggregator EXECUTION_STATUS_AGGREGATOR = new ExecutionStatusAggregator();

    /**
     * Tests that the aggregator calculates the correct workflow RunExecution from a list of task RunExecutions.
     */
    @Test
    void testGetWorkflowExecutionFromTaskExecutions() {
        // Empty TaskExecutions should return Optional.empty()
        Optional<RunExecution> workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(new TaskExecutions());
        assertTrue(workflowExecution.isEmpty());

        // The workflow execution generated from a single task should have the same status as the one task
        TaskExecutions taskExecutions = new TaskExecutions().taskExecutions(List.of(new RunExecution().executionStatus(SUCCESSFUL)));
        workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(SUCCESSFUL, workflowExecution.get().getExecutionStatus());

        // The workflow execution generated from tasks that were all successful should have a successful status
        taskExecutions.setTaskExecutions(List.of(
                new RunExecution().executionStatus(SUCCESSFUL),
                new RunExecution().executionStatus(SUCCESSFUL),
                new RunExecution().executionStatus(SUCCESSFUL)
        ));
        workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(SUCCESSFUL, workflowExecution.get().getExecutionStatus());

        // The workflow execution generated from tasks that where there are failures should have the most frequent failed status
        taskExecutions.setTaskExecutions(List.of(
                new RunExecution().executionStatus(SUCCESSFUL),
                new RunExecution().executionStatus(FAILED_SEMANTIC_INVALID),
                new RunExecution().executionStatus(FAILED_RUNTIME_INVALID),
                new RunExecution().executionStatus(FAILED_RUNTIME_INVALID)
        ));
        workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        // Should be FAILED_RUNTIME_INVALID because it's the most frequent failed status
        assertEquals(FAILED_RUNTIME_INVALID, workflowExecution.get().getExecutionStatus());
        // When there are equal counts of all failed statuses, the workflow execution status should be one of them
        taskExecutions.setTaskExecutions(List.of(
                new RunExecution().executionStatus(SUCCESSFUL),
                new RunExecution().executionStatus(FAILED_SEMANTIC_INVALID),
                new RunExecution().executionStatus(FAILED_RUNTIME_INVALID)
        ));
        workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertTrue(workflowExecution.get().getExecutionStatus() == FAILED_RUNTIME_INVALID
                || workflowExecution.get().getExecutionStatus() == FAILED_SEMANTIC_INVALID);
    }

    /**
     * Tests that the aggregator calculates the correct aggregated metric from a list of workflow RunExecutions
     */
    @Test
    void testGetAggregatedMetricFromWorkflowExecutions() {
        // Empty list should return Optional.empty()
        Optional<ExecutionStatusMetric> executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromExecutions(List.of());
        assertTrue(executionStatusMetric.isEmpty());

        // Test the metric calculated from a single workflow execution.
        List<RunExecution> workflowExecutions = List.of(new RunExecution().executionStatus(SUCCESSFUL));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()));
        assertFalse(executionStatusMetric.get().getCount().containsKey(FAILED_SEMANTIC_INVALID.toString()));
        assertFalse(executionStatusMetric.get().getCount().containsKey(FAILED_RUNTIME_INVALID.toString()));

        // Test the metric calculated from multiple workflow executions.
        workflowExecutions = List.of(
                new RunExecution().executionStatus(SUCCESSFUL),
                new RunExecution().executionStatus(FAILED_SEMANTIC_INVALID),
                new RunExecution().executionStatus(FAILED_RUNTIME_INVALID)
        );
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()));
        assertEquals(1, executionStatusMetric.get().getCount().get(FAILED_SEMANTIC_INVALID.toString()));
        assertEquals(1, executionStatusMetric.get().getCount().get(FAILED_RUNTIME_INVALID.toString()));
    }
}
