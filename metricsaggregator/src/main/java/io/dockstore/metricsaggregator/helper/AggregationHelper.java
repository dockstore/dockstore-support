package io.dockstore.metricsaggregator.helper;

import static java.util.stream.Collectors.groupingBy;

import io.dockstore.metricsaggregator.Statistics;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AggregationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationHelper.class);

    private AggregationHelper() {}

    /**
     * Aggregate metrics from the list of executions.
     * @param executions
     * @return Metrics object containing aggregated metrics
     */
    public static Optional<Metrics> getAggregatedMetrics(List<Execution> executions) {
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = getAggregatedExecutionStatus(executions);
        if (getAggregatedExecutionStatus(executions).isPresent()) {
            Metrics aggregatedMetrics = new Metrics();
            aggregatedMetrics.setExecutionStatusCount(aggregatedExecutionStatus.get());
            getAggregatedExecutionTime(executions).ifPresent(aggregatedMetrics::setExecutionTime);
            getAggregatedCpu(executions).ifPresent(aggregatedMetrics::setCpu);
            getAggregatedMemory(executions).ifPresent(aggregatedMetrics::setMemory);
            return Optional.of(aggregatedMetrics);
        }
        return Optional.empty();
    }

    /**
     * Aggregate Execution Status metrics from the list of executions by summing up the count of each Execution Status encountered in the list of executions.
     * @param executions
     * @return
     */
    public static Optional<ExecutionStatusMetric> getAggregatedExecutionStatus(List<Execution> executions) {
        Map<String, Integer> statusCount = executions.stream()
                .map(execution -> execution.getExecutionStatus().toString())
                .collect(groupingBy(Function.identity(), Collectors.reducing(0, e -> 1, Integer::sum)));
        // This shouldn't happen because all executions should have an execution status, but check anyway
        if (statusCount.isEmpty()) {
            return Optional.empty();
        }
        // Don't need to set the other fields because the setter for count will calculate and set the other fields
        return Optional.of(new ExecutionStatusMetric().count(statusCount));
    }

    /**
     * Aggregate Execution Time metrics from the list of executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<ExecutionTimeMetric> getAggregatedExecutionTime(List<Execution> executions) {
        List<String> executionTimes = executions.stream()
                .map(Execution::getExecutionTime)
                .filter(Objects::nonNull)
                .toList();
        List<Double> executionTimesInSeconds = executionTimes.stream()
                .map(executionTime -> {
                    // Convert executionTime in ISO 8601 duration format to seconds
                    Optional<Duration> parsedISO8601ExecutionTime = checkExecutionTimeISO8601Format(executionTime);
                    if (parsedISO8601ExecutionTime.isPresent()) {
                        return Long.valueOf(parsedISO8601ExecutionTime.get().toSeconds()).doubleValue();
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (!executionTimesInSeconds.isEmpty()) {
            Statistics statistics = new Statistics(executionTimesInSeconds);
            return Optional.of(new ExecutionTimeMetric()
                    .minimum(statistics.min())
                    .maximum(statistics.max())
                    .average(statistics.average())
                    .numberOfDataPointsForAverage(statistics.numberOfDataPoints())
            );
        }
        return Optional.empty();
    }

    /**
     * Check that the execution time is in ISO-1806 format by parsing it into a Duration.
     * @param executionTime ISO 18601 execution time
     * @return Duration parsed from the ISO 18601 execution time
     */
    static Optional<Duration> checkExecutionTimeISO8601Format(String executionTime) {
        try {
            return Optional.of(Duration.parse(executionTime));
        } catch (DateTimeParseException e) {
            LOG.error("Execution time {} is not in ISO 8601 format and could not parsed to a  Duration", executionTime, e);
            return Optional.empty();
        }
    }

    /**
     * Aggregate CPU metrics from the list of executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<CpuMetric> getAggregatedCpu(List<Execution> executions) {
        List<Double> cpuRequirements = executions.stream()
                .map(Execution::getCpuRequirements)
                .filter(Objects::nonNull)
                .map(Integer::doubleValue)
                .toList();
        if (!cpuRequirements.isEmpty()) {
            Statistics statistics = new Statistics(cpuRequirements);
            return Optional.of(new CpuMetric()
                    .minimum(statistics.min())
                    .maximum(statistics.max())
                    .average(statistics.average())
                    .numberOfDataPointsForAverage(statistics.numberOfDataPoints()));
        }
        return Optional.empty();
    }

    /**
     * Aggregate Memory metrics from the list of executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<MemoryMetric> getAggregatedMemory(List<Execution> executions) {
        List<Double> memoryRequirements = executions.stream()
                .map(Execution::getMemoryRequirementsGB)
                .filter(Objects::nonNull)
                .toList();
        if (!memoryRequirements.isEmpty()) {
            Statistics statistics = new Statistics(memoryRequirements);
            return Optional.of(new MemoryMetric()
                    .minimum(statistics.min())
                    .maximum(statistics.max())
                    .average(statistics.average())
                    .numberOfDataPointsForAverage(statistics.numberOfDataPoints()));
        }
        return Optional.empty();
    }
}
