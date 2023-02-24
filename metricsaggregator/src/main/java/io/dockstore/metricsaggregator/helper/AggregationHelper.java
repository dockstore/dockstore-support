package io.dockstore.metricsaggregator.helper;

import static java.util.stream.Collectors.groupingBy;

import io.dockstore.metricsaggregator.Statistics;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
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

    public static Optional<ExecutionStatusMetric> getAggregatedExecutionStatus(List<Execution> executions) {
        Map<String, Integer> statusCount = executions.stream()
                .map(execution -> execution.getExecutionStatus().toString())
                .collect(groupingBy(Function.identity(), Collectors.reducing(0, e -> 1, Integer::sum)));
        // This shouldn't happen because all executions should have an execution status, but check anyway
        if (statusCount.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ExecutionStatusMetric().count(statusCount));
    }

    public static Optional<ExecutionTimeMetric> getAggregatedExecutionTime(List<Execution> executions) {
        List<String> executionTimes = executions.stream()
                .map(Execution::getExecutionTime)
                .filter(Objects::nonNull)
                .toList();
        List<Double> executionTimesInSeconds = executionTimes.stream()
                .map(executionTime -> {
                    // Convert executionTime in ISO 8601 duration format to seconds
                    try {
                        return Long.valueOf(Duration.parse(executionTime).toSeconds()).doubleValue();
                    } catch (DateTimeParseException e) {
                        LOG.error("Could not parse Duration from {}", executionTime);
                        return null;
                    }
                })
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

    public static Optional<CpuMetric> getAggregatedCpu(List<Execution> executions) {
        List<Double> cpuRequirements = executions.stream().map(Execution::getCpuRequirements).filter(Objects::nonNull).map(
                Integer::doubleValue).toList();
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

    public static Optional<MemoryMetric> getAggregatedMemory(List<Execution> executions) {
        List<String> memoryRequirements = executions.stream().map(Execution::getMemoryRequirements).filter(Objects::nonNull).toList();
        if (!memoryRequirements.isEmpty()) {
            List<Double> memoryDoubles = memoryRequirements.stream()
                    .map(memoryString -> memoryString.split(" "))
                    // Only aggregate memory specified in the following format: Numerical value, space, "GB". Ex: "2 GB"
                    .filter(splitMemoryString -> splitMemoryString.length == 2 && MemoryStatisticMetric.UNIT.equals(splitMemoryString[1]))
                    .map(splitMemoryString -> {
                        try {
                            return Double.parseDouble(splitMemoryString[0]);
                        } catch (NumberFormatException e) {
                            LOG.error("Could not parse integer from {}", splitMemoryString[0]);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            if (!memoryDoubles.isEmpty()) {
                Statistics statistics = new Statistics(memoryDoubles);
                return Optional.of(new MemoryMetric()
                        .minimum(statistics.min())
                        .maximum(statistics.max())
                        .average(statistics.average())
                        .numberOfDataPointsForAverage(statistics.numberOfDataPoints()));
            }
        }
        return Optional.empty();
    }
}
