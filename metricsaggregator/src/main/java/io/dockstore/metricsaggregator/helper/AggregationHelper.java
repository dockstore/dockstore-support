package io.dockstore.metricsaggregator.helper;

import static io.dockstore.common.metrics.FormatCheckHelper.checkExecutionDateISO8601Format;
import static java.util.stream.Collectors.groupingBy;

import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import io.dockstore.openapi.client.model.ValidatorInfo;
import io.dockstore.openapi.client.model.ValidatorVersionInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AggregationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationHelper.class);

    private AggregationHelper() {
    }

    /**
     * Aggregate metrics from all submissions.
     *
     * @param allSubmissions
     * @return Metrics object containing aggregated metrics
     */
    public static Optional<Metrics> getAggregatedMetrics(ExecutionsRequestBody allSubmissions) {
        Metrics aggregatedMetrics = new Metrics();
        // Set run metrics
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = new ExecutionStatusAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions);
        boolean containsRunMetrics = aggregatedExecutionStatus.isPresent();
        if (aggregatedExecutionStatus.isPresent()) {
            aggregatedMetrics.setExecutionStatusCount(aggregatedExecutionStatus.get());
            new ExecutionTimeAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions).ifPresent(aggregatedMetrics::setExecutionTime);
            new CpuAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions).ifPresent(aggregatedMetrics::setCpu);
            new MemoryAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions).ifPresent(aggregatedMetrics::setMemory);
            new CostAggregator().getAggregatedMetricFromAllSubmissions(allSubmissions).ifPresent(aggregatedMetrics::setCost);
        }

        // Set validation metrics
        Optional<ValidationStatusMetric> aggregatedValidationStatus = getAggregatedValidationStatus(allSubmissions);
        boolean containsValidationMetrics = aggregatedValidationStatus.isPresent();
        aggregatedValidationStatus.ifPresent(aggregatedMetrics::setValidationStatus);

        // Only return aggregated metrics if it contains either run metrics or validation metrics
        if (containsRunMetrics || containsValidationMetrics) {
            return Optional.of(aggregatedMetrics);
        }

        return Optional.empty();
    }

    /**
     * Aggregate Validation metrics from the list of validation executions by retrieving the validation information for the most recent execution of
     * each validator tool version.
     * @param allSubmissions
     * @return
     */
    public static Optional<ValidationStatusMetric> getAggregatedValidationStatus(ExecutionsRequestBody allSubmissions) {
        // Get aggregated ValidationStatus metrics that were submitted to Dockstore
        List<ValidationStatusMetric> validationStatusMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(Metrics::getValidationStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        getAggregatedValidationStatusFromExecutions(allSubmissions.getValidationExecutions()).ifPresent(validationStatusMetrics::add);

        Map<String, ValidatorInfo> newValidatorToolToValidatorInfo = new HashMap<>();
        if (!validationStatusMetrics.isEmpty()) {
            // Go through all the ValidationStatusMetrics and group the ValidationVersionInfos by validator tool
            Map<String, List<ValidatorVersionInfo>> validatorToolToValidationVersionInfos = validationStatusMetrics.stream()
                    .map(ValidationStatusMetric::getValidatorTools)
                    .flatMap(validatorToolToValidatorInfoMap -> validatorToolToValidatorInfoMap.entrySet().stream())
                    .collect(groupingBy(Map.Entry::getKey, Collectors.flatMapping(entry -> {
                        ValidatorInfo validationInfoForValidatorTool = entry.getValue();
                        return validationInfoForValidatorTool.getValidatorVersions().stream();
                    }, Collectors.toList())));

            // For each validator tool, find the most recent ValidatorVersionInfo for each version
            validatorToolToValidationVersionInfos.forEach((validatorTool, validationVersionInfosByValidatorTool) -> {
                // Number of runs across all versions
                final int numberOfRuns = validationVersionInfosByValidatorTool.stream().map(ValidatorVersionInfo::getNumberOfRuns)
                        .mapToInt(Integer::intValue)
                        .sum();
                final List<DoubleStatistics> validationRunsStatistics = validationVersionInfosByValidatorTool.stream()
                        .map(validatorVersionInfo -> new DoubleStatistics(validatorVersionInfo.getPassingRate(), validatorVersionInfo.getNumberOfRuns()))
                        .toList();

                final double passingRate = DoubleStatistics.createFromStatistics(validationRunsStatistics).getAverage();
                final Optional<ValidatorVersionInfo> mostRecentValidationVersion = getLatestValidationVersionInfo(validationVersionInfosByValidatorTool);

                if (mostRecentValidationVersion.isPresent()) {
                    // Group ValidatorVersionInfo by version name
                    Map<String, List<ValidatorVersionInfo>> versionNameToValidationVersionInfos = validationVersionInfosByValidatorTool.stream()
                            .collect(Collectors.groupingBy(ValidatorVersionInfo::getName));

                    // Get a list of the most recent ValidatorVersionInfo for each version
                    List<ValidatorVersionInfo> mostRecentValidationVersionInfos = versionNameToValidationVersionInfos.values().stream().map(AggregationHelper::getLatestValidationVersionInfo).filter(Optional::isPresent).map(Optional::get).toList();

                    // Set validation info for the validator tool
                    ValidatorInfo validatorInfo = new ValidatorInfo()
                            .mostRecentVersionName(mostRecentValidationVersion.get().getName())
                            .validatorVersions(mostRecentValidationVersionInfos)
                            .numberOfRuns(numberOfRuns)
                            .passingRate(passingRate);

                    newValidatorToolToValidatorInfo.put(validatorTool, validatorInfo);
                }
            });
        }

        if (newValidatorToolToValidatorInfo.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ValidationStatusMetric().validatorTools(newValidatorToolToValidatorInfo));
    }

    /**
     * Aggregate Validation metrics from the list of validation executions by retrieving the validation information for the most recent execution of
     * each validator tool version.
     * @param executions
     * @return
     */
    public static Optional<ValidationStatusMetric> getAggregatedValidationStatusFromExecutions(List<ValidationExecution> executions) {
        if (executions.isEmpty()) {
            return Optional.empty();
        }

        // Group executions by validator tool
        Map<ValidationExecution.ValidatorToolEnum, List<ValidationExecution>> validatorToolToValidations = executions.stream()
                .collect(groupingBy(ValidationExecution::getValidatorTool));

        // For each validator tool, aggregate validation metrics for it
        Map<String, ValidatorInfo> validatorToolToValidationInfo = new HashMap<>();
        validatorToolToValidations.forEach((validatorTool, validatorToolExecutions) -> {
            Optional<ValidationExecution> latestValidationExecution = getLatestValidationExecution(validatorToolExecutions);

            if (latestValidationExecution.isPresent()) {
                // Group the validation executions for the validator tool by version
                Map<String, List<ValidationExecution>> validatorVersionNameToValidationExecutions = validatorToolExecutions.stream()
                        .collect(groupingBy(ValidationExecution::getValidatorToolVersion));

                // Get the validation information for the most recent execution for each validator tool version
                Map<String, ValidatorVersionInfo> validatorVersionNameToVersionInfo = new HashMap<>();
                validatorVersionNameToValidationExecutions.forEach((validatorVersionName, validatorVersionExecutions) -> {
                    Optional<ValidationExecution> latestValidationExecutionForVersion = getLatestValidationExecution(validatorVersionExecutions);

                    latestValidationExecutionForVersion.ifPresent(validationExecution -> {
                        ValidatorVersionInfo validatorVersionInfo = new ValidatorVersionInfo()
                                .name(validatorVersionName)
                                .isValid(validationExecution.isIsValid())
                                .dateExecuted(validationExecution.getDateExecuted())
                                .numberOfRuns(validatorVersionExecutions.size())
                                .passingRate(getPassingRate(validatorVersionExecutions));

                        if (!validationExecution.isIsValid() && StringUtils.isNotBlank(validationExecution.getErrorMessage())) {
                            validatorVersionInfo.errorMessage(validationExecution.getErrorMessage());
                        }

                        validatorVersionNameToVersionInfo.put(validatorVersionName, validatorVersionInfo);
                    });
                });

                // Set validation info for the validator tool
                ValidatorInfo validatorInfo = new ValidatorInfo()
                        .mostRecentVersionName(validatorVersionNameToVersionInfo.get(latestValidationExecution.get().getValidatorToolVersion()).getName())
                        .validatorVersions(validatorVersionNameToVersionInfo.values().stream().toList())
                        .numberOfRuns(validatorToolExecutions.size())
                        .passingRate(getPassingRate(validatorToolExecutions));

                validatorToolToValidationInfo.put(validatorTool.toString(), validatorInfo);
            }
        });

        // This shouldn't happen because all validation executions should the required fields, but check anyway
        if (validatorToolToValidationInfo.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ValidationStatusMetric().validatorTools(validatorToolToValidationInfo));
    }

    static Optional<ValidationExecution> getLatestValidationExecution(List<ValidationExecution> executions) {
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

    static Optional<ValidatorVersionInfo> getLatestValidationVersionInfo(List<ValidatorVersionInfo> validationVersionInfos) {
        if (validationVersionInfos.isEmpty()) {
            return Optional.empty();
        }

        boolean containsInvalidDate = validationVersionInfos.stream().anyMatch(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).isEmpty());
        if (containsInvalidDate) {
            return Optional.empty();
        }

        return validationVersionInfos.stream()
                .max(Comparator.comparing(validatorVersionInfo -> checkExecutionDateISO8601Format(validatorVersionInfo.getDateExecuted()).get(), Date::compareTo));
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
