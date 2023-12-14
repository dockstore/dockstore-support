package io.dockstore.metricsaggregator.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemoryAggregatorTest {
    private static final MemoryAggregator MEMORY_AGGREGATOR = new MemoryAggregator();

    /**
     * Tests that the aggregator calculates the correct workflow RunExecution from a TaskExecutions that represents a list of task RunExecutions that were executed during a single workflow execution.
     */
    @Test
    void testGetWorkflowExecutionFromTaskExecutions() {
        // Empty TaskExecutions should return Optional.empty()
        Optional<RunExecution> workflowExecution = MEMORY_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(new TaskExecutions());
        assertTrue(workflowExecution.isEmpty());

        // The workflow execution generated from a single task should have the same memoryRequirementsGB as the one task
        TaskExecutions taskExecutions = new TaskExecutions().taskExecutions(List.of(new RunExecution().memoryRequirementsGB(1.0)));
        workflowExecution = MEMORY_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(1.0, workflowExecution.get().getMemoryRequirementsGB());

        // The workflow execution generated from multiple tasks should have the memoryRequirementsGB from the highest memoryRequirementsGB from the list of tasks
        taskExecutions.setTaskExecutions(List.of(
                new RunExecution().memoryRequirementsGB(1.0),
                new RunExecution().memoryRequirementsGB(2.0),
                new RunExecution().memoryRequirementsGB(3.0)
        ));
        workflowExecution = MEMORY_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(3.0, workflowExecution.get().getMemoryRequirementsGB());
    }

    /**
     * Tests that the aggregator calculates the correct aggregated metric from a list of workflow RunExecutions
     */
    @Test
    void testGetAggregatedMetricFromWorkflowExecutions() {
        // Empty list should return Optional.empty()
        Optional<MemoryMetric> memoryMetric = MEMORY_AGGREGATOR.getAggregatedMetricFromWorkflowExecutions(List.of());
        assertTrue(memoryMetric.isEmpty());

        // Test the memory metric calculated from a single workflow execution. The min, max, and average should be the same value as the single execution
        List<RunExecution> workflowExecutions = List.of(new RunExecution().memoryRequirementsGB(1.0));
        memoryMetric = MEMORY_AGGREGATOR.getAggregatedMetricFromWorkflowExecutions(workflowExecutions);
        assertTrue(memoryMetric.isPresent());
        assertEquals(1.0, memoryMetric.get().getMinimum());
        assertEquals(1.0, memoryMetric.get().getMaximum());
        assertEquals(1.0, memoryMetric.get().getAverage());
        assertEquals(1, memoryMetric.get().getNumberOfDataPointsForAverage());

        // Test the memory metric calculated from multiple workflow executions.
        workflowExecutions = List.of(
                new RunExecution().memoryRequirementsGB(2.0),
                new RunExecution().memoryRequirementsGB(4.0),
                new RunExecution().memoryRequirementsGB(6.0)
        );
        memoryMetric = MEMORY_AGGREGATOR.getAggregatedMetricFromWorkflowExecutions(workflowExecutions);
        assertTrue(memoryMetric.isPresent());
        assertEquals(2.0, memoryMetric.get().getMinimum());
        assertEquals(6.0, memoryMetric.get().getMaximum());
        assertEquals(4.0, memoryMetric.get().getAverage());
        assertEquals(3, memoryMetric.get().getNumberOfDataPointsForAverage());
    }
}