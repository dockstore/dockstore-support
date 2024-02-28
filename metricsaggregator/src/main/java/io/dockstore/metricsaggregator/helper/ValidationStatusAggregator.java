package io.dockstore.metricsaggregator.helper;

import static io.dockstore.common.metrics.FormatCheckHelper.checkExecutionDateISO8601Format;
import static java.util.stream.Collectors.groupingBy;

import io.dockstore.common.metrics.ValidationExecution;
import io.dockstore.common.metrics.ValidationExecution.ValidatorTool;
import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import io.dockstore.openapi.client.model.ValidatorInfo;
import io.dockstore.openapi.client.model.ValidatorVersionInfo;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Aggregate Validation metrics from the list of validation executions by retrieving the validation information for the most recent execution of
 * each validator tool version.
 */
public class ValidationStatusAggregator extends ValidationExecutionAggregator<ValidationStatusMetric, ValidationExecution> {

    @Override
    public ValidationExecution getMetricFromExecution(ValidationExecution execution) {
        return execution; // The entire execution contains the metric, not a specific field like with RunExecution
    }

    @Override
    public boolean validateExecutionMetric(ValidationExecution executionMetric) {
        return true;
    }

    @Override
    public String getPropertyPathToValidate() {
        return "";
    }

    /**
     * Aggregate Validation metrics from the list of validation executions by retrieving the validation information for the most recent execution of
     * each validator tool version.
     * @param executionMetrics
     * @return
     */
    @Override
    protected Optional<ValidationStatusMetric> calculateAggregatedMetricFromExecutionMetrics(List<ValidationExecution> executionMetrics) {
        if (executionMetrics.isEmpty()) {
            return Optional.empty();
        }

        // Group executions by validator tool
        Map<ValidatorTool, List<ValidationExecution>> validatorToolToValidations = executionMetrics.stream()
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
                                .isValid(validationExecution.getIsValid())
                                .dateExecuted(validationExecution.getDateExecuted())
                                .numberOfRuns(validatorVersionExecutions.size())
                                .passingRate(getPassingRate(validatorVersionExecutions));

                        if (!validationExecution.getIsValid() && StringUtils.isNotBlank(validationExecution.getErrorMessage())) {
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

    @Override
    protected Optional<ValidationStatusMetric> calculateAggregatedMetricFromAggregatedMetrics(
            List<ValidationStatusMetric> aggregatedMetrics) {
        Map<String, ValidatorInfo> newValidatorToolToValidatorInfo = new HashMap<>();
        if (!aggregatedMetrics.isEmpty()) {
            // Go through all the ValidationStatusMetrics and group the ValidationVersionInfos by validator tool
            Map<String, List<ValidatorVersionInfo>> validatorToolToValidationVersionInfos = aggregatedMetrics.stream()
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
                    List<ValidatorVersionInfo> mostRecentValidationVersionInfos = versionNameToValidationVersionInfos.values().stream().map(this::getLatestValidationVersionInfo).filter(Optional::isPresent).map(Optional::get).toList();

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

    Optional<ValidatorVersionInfo> getLatestValidationVersionInfo(List<ValidatorVersionInfo> validationVersionInfos) {
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
    static double getPassingRate(List<ValidationExecution> executions) {
        final int oneHundredPercent = 100;
        final double numberOfPassingExecutions = executions.stream()
                .filter(ValidationExecution::getIsValid)
                .count();

        return (numberOfPassingExecutions / executions.size()) * oneHundredPercent;
    }
}
