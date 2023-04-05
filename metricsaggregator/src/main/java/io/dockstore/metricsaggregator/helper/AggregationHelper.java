package io.dockstore.metricsaggregator.helper;

import static io.dockstore.webservice.core.metrics.RunExecution.checkExecutionTimeISO8601Format;
import static io.dockstore.webservice.core.metrics.ValidationExecution.checkExecutionDateISO8601Format;
import static java.util.stream.Collectors.groupingBy;

import io.dockstore.metricsaggregator.Statistics;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationInfo;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AggregationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationHelper.class);

    private AggregationHelper() {}

    /**
     * Aggregate metrics from the list of executions.
     *
     * @param runExecutions
     * @param validationExecutions
     * @return Metrics object containing aggregated metrics
     */
    public static Optional<Metrics> getAggregatedMetrics(List<RunExecution> runExecutions, List<ValidationExecution> validationExecutions) {
        Metrics aggregatedMetrics = new Metrics();
        // Set run metrics
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = getAggregatedExecutionStatus(runExecutions);
        boolean containsRunMetrics = aggregatedExecutionStatus.isPresent();
        if (aggregatedExecutionStatus.isPresent()) {
            aggregatedMetrics.setExecutionStatusCount(aggregatedExecutionStatus.get());
            getAggregatedExecutionTime(runExecutions).ifPresent(aggregatedMetrics::setExecutionTime);
            getAggregatedCpu(runExecutions).ifPresent(aggregatedMetrics::setCpu);
            getAggregatedMemory(runExecutions).ifPresent(aggregatedMetrics::setMemory);
        }

        // Set validation metrics
        Optional<ValidationStatusMetric> aggregatedValidationStatus = getAggregatedValidationStatus(validationExecutions);
        boolean containsValidationMetrics = aggregatedValidationStatus.isPresent();
        aggregatedValidationStatus.ifPresent(aggregatedMetrics::setValidationStatus);

        // Only return aggregated metrics if it contains either run metrics or validation metrics
        if (containsRunMetrics || containsValidationMetrics) {
            return Optional.of(aggregatedMetrics);
        }

        return Optional.empty();
    }

    /**
     * Aggregate Execution Status metrics from the list of run executions by summing up the count of each Execution Status encountered in the list of executions.
     * @param executions
     * @return
     */
    public static Optional<ExecutionStatusMetric> getAggregatedExecutionStatus(List<RunExecution> executions) {
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
     * Aggregate Execution Time metrics from the list of run executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<ExecutionTimeMetric> getAggregatedExecutionTime(List<RunExecution> executions) {
        List<String> executionTimes = executions.stream()
                .map(RunExecution::getExecutionTime)
                .filter(Objects::nonNull)
                .toList();


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
     * Aggregate CPU metrics from the list of run executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<CpuMetric> getAggregatedCpu(List<RunExecution> executions) {
        List<Double> cpuRequirements = executions.stream()
                .map(RunExecution::getCpuRequirements)
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
     * Aggregate Memory metrics from the list of run executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<MemoryMetric> getAggregatedMemory(List<RunExecution> executions) {
        List<Double> memoryRequirements = executions.stream()
                .map(RunExecution::getMemoryRequirementsGB)
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

    /**
     * Aggregate Validation metrics from the list of validation executions by performing a logical AND.
     * @param executions
     * @return
     */
    public static Optional<ValidationStatusMetric> getAggregatedValidationStatus(List<ValidationExecution> executions) {
        if (executions.isEmpty()) {
            return Optional.empty();
        }

        // Group executions by validator tool
        Map<ValidationExecution.ValidatorToolEnum, List<ValidationExecution>> validatorToolToValidations = executions.stream()
                .collect(groupingBy(ValidationExecution::getValidatorTool));

        // For each validator tool, aggregate validation metrics for it
        Map<String, ValidationInfo> validatorToolToValidationInfo = new HashMap<>();
        validatorToolToValidations.forEach((validatorTool, validatorToolExecutions) -> {
            Optional<ValidationExecution> latestValidation = getLatestValidation(validatorToolExecutions);

            if (latestValidation.isPresent()) {
                // Group the validation executions for the validator tool by version
                Map<String, List<ValidationExecution>> validatorToolVersionToValidations = validatorToolExecutions.stream()
                        .collect(groupingBy(ValidationExecution::getValidatorToolVersion));

                // Get the most recent isValid value for the validator tool version
                Map<String, Boolean> validatorToolVersionToMostRecentIsValid = new HashMap<>();
                validatorToolVersionToValidations.forEach((validatorToolVersion, validatorToolVersionExecutions) -> {
                    Optional<ValidationExecution> latestValidationExecutionForVersion = getLatestValidation(validatorToolVersionExecutions);
                    latestValidationExecutionForVersion.ifPresent(
                            validationExecution -> validatorToolVersionToMostRecentIsValid.put(validatorToolVersion, validationExecution.isIsValid()));
                });

                List<String> successfulValidationVersions = validatorToolVersionToMostRecentIsValid.entrySet().stream()
                        .filter(Map.Entry::getValue) // Filter for successful validations
                        .map(Map.Entry::getKey) // Map to validator version
                        .distinct()
                        .toList();

                List<String> failedValidationVersions = validatorToolVersionToMostRecentIsValid.entrySet().stream()
                        .filter(e -> !e.getValue()) // Filter for failed validations
                        .map(Map.Entry::getKey) // Map to validator version
                        .distinct()
                        .toList();

                ValidationInfo validationInfo = new ValidationInfo()
                        .mostRecentIsValid(latestValidation.get().isIsValid())
                        .mostRecentVersion(latestValidation.get().getValidatorToolVersion())
                        .successfulValidationVersions(successfulValidationVersions)
                        .failedValidationVersions(failedValidationVersions)
                        .numberOfRuns(validatorToolExecutions.size())
                        .passingRate(getPassingRate(validatorToolExecutions));

                if (!latestValidation.get().isIsValid() && StringUtils.isNotBlank(latestValidation.get().getErrorMessage())) {
                    validationInfo.mostRecentErrorMessage(latestValidation.get().getErrorMessage());
                }

                validatorToolToValidationInfo.put(validatorTool.toString(), validationInfo);
            }
        });

        // This shouldn't happen because all validation executions should the required fields, but check anyway
        if (validatorToolToValidationInfo.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ValidationStatusMetric().validatorToolToIsValid(validatorToolToValidationInfo));
    }

    static Optional<ValidationExecution> getLatestValidation(List<ValidationExecution> executions) {
        if (executions.isEmpty()) {
            return Optional.empty();
        }

        boolean containsInvalidDate = executions.stream().anyMatch(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).isEmpty());
        if (containsInvalidDate) {
            return Optional.empty();
        }

        return executions.stream()
                .max(Comparator.comparing(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).get(), Date::compareTo));
    }

    /**
     * Gets the percentage of executions that passed validation
     * @param executions
     * @return
     */
    @SuppressWarnings("checkstyle:magicnumber")
    static double getPassingRate(List<ValidationExecution> executions) {
        final double numberOfPassingExecutions = executions.stream()
                .filter(ValidationExecution::isIsValid)
                .count();

        return (numberOfPassingExecutions / executions.size()) * 100;
    }
}
