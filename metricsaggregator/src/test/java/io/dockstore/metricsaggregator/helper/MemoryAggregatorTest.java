package io.dockstore.metricsaggregator.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.openapi.client.model.MemoryMetric;
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
        TaskExecutions taskExecutions = new TaskExecutions();
        taskExecutions.setTaskExecutions(List.of(createRunExecution(1.0)));
        workflowExecution = MEMORY_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(1.0, workflowExecution.get().getMemoryRequirementsGB());

        // The workflow execution generated from multiple tasks should have the memoryRequirementsGB from the highest memoryRequirementsGB from the list of tasks
        taskExecutions.setTaskExecutions(List.of(
                createRunExecution(1.0),
                createRunExecution(2.0),
                createRunExecution(3.0)
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
        Optional<MemoryMetric> memoryMetric = MEMORY_AGGREGATOR.getAggregatedMetricFromExecutions(List.of());
        assertTrue(memoryMetric.isEmpty());

        // Test the memory metric calculated from a single workflow execution. The min, max, and average should be the same value as the single execution
        List<RunExecution> workflowExecutions = List.of(createRunExecution(1.0));
        memoryMetric = MEMORY_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(memoryMetric.isPresent());
        assertEquals(1.0, memoryMetric.get().getMinimum());
        assertEquals(1.0, memoryMetric.get().getMaximum());
        assertEquals(1.0, memoryMetric.get().getAverage());
        assertEquals(1, memoryMetric.get().getNumberOfDataPointsForAverage());

        // Test the memory metric calculated from multiple workflow executions.
        workflowExecutions = List.of(
                createRunExecution(2.0),
                createRunExecution(4.0),
                createRunExecution(6.0)
        );
        memoryMetric = MEMORY_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(memoryMetric.isPresent());
        assertEquals(2.0, memoryMetric.get().getMinimum());
        assertEquals(6.0, memoryMetric.get().getMaximum());
        assertEquals(4.0, memoryMetric.get().getAverage());
        assertEquals(3, memoryMetric.get().getNumberOfDataPointsForAverage());
    }

    private RunExecution createRunExecution(Double memoryRequirementGB) {
        RunExecution runExecution = new RunExecution();
        runExecution.setMemoryRequirementsGB(memoryRequirementGB);
        return runExecution;
    }
}
