package io.dockstore.metricsaggregator.helper;

import static io.dockstore.metricsaggregator.common.TestUtilities.createValidationExecution;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import io.dockstore.openapi.client.model.ValidatorInfo;
import io.dockstore.openapi.client.model.ValidatorVersionInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AggregationHelperTest {

    @Test
    void testGetAggregatedValidationStatus() {
        List<ValidationExecution> executions = new ArrayList<>();
        Optional<ValidationStatusMetric> validationStatusMetric = new ValidationStatusAggregator().getAggregatedMetricFromAllSubmissions(new ExecutionsRequestBody().validationExecutions(executions));
        assertTrue(validationStatusMetric.isEmpty());

        // Add an execution with validation data
        final ValidationExecution.ValidatorToolEnum validatorTool = ValidationExecution.ValidatorToolEnum.MINIWDL;
        final String validatorToolVersion1 = "1.0";
        executions.add(createValidationExecution(validatorTool, validatorToolVersion1, true));
        validationStatusMetric = new ValidationStatusAggregator().getAggregatedMetricFromAllSubmissions(new ExecutionsRequestBody().validationExecutions(executions));
        assertTrue(validationStatusMetric.isPresent());
        ValidatorInfo validatorInfo = validationStatusMetric.get().getValidatorTools().get(validatorTool.toString());
        assertNotNull(validatorInfo);
        assertNotNull(validatorInfo.getMostRecentVersionName());
        ValidatorVersionInfo mostRecentValidatorVersion = validatorInfo.getValidatorVersions().stream().filter(validationVersion -> validatorToolVersion1.equals(validationVersion.getName())).findFirst().get();
        assertTrue(mostRecentValidatorVersion.isIsValid());
        assertEquals(validatorToolVersion1, mostRecentValidatorVersion.getName());
        assertNull(mostRecentValidatorVersion.getErrorMessage());
        assertEquals(1, mostRecentValidatorVersion.getNumberOfRuns());
        assertEquals(100, mostRecentValidatorVersion.getPassingRate());
        assertEquals(1, validatorInfo.getValidatorVersions().size(), "There should be 2 ValidatorVersionInfo objects because 1 version was ran");
        assertEquals(1, validatorInfo.getNumberOfRuns());
        assertEquals(100, validatorInfo.getPassingRate());

        // Add an execution that isn't valid for the same validator
        final String validatorToolVersion2 = "2.0";
        executions.add(createValidationExecution(validatorTool, validatorToolVersion2, false).errorMessage("This is an error message"));
        validationStatusMetric = new ValidationStatusAggregator().getAggregatedMetricFromAllSubmissions(new ExecutionsRequestBody().validationExecutions(executions));
        assertTrue(validationStatusMetric.isPresent());
        validatorInfo = validationStatusMetric.get().getValidatorTools().get(validatorTool.toString());
        mostRecentValidatorVersion = validatorInfo.getValidatorVersions().stream().filter(validatorVersion -> validatorToolVersion2.equals(validatorVersion.getName())).findFirst().get();
        assertFalse(mostRecentValidatorVersion.isIsValid());
        assertEquals(validatorToolVersion2, mostRecentValidatorVersion.getName());
        assertEquals("This is an error message", mostRecentValidatorVersion.getErrorMessage());
        assertEquals(1, mostRecentValidatorVersion.getNumberOfRuns());
        assertEquals(0, mostRecentValidatorVersion.getPassingRate());
        assertEquals(2, validatorInfo.getValidatorVersions().size(), "There should be 2 ValidatorVersionInfo objects because 2 versions were ran");
        assertEquals(2, validatorInfo.getNumberOfRuns());
        assertEquals(50, validatorInfo.getPassingRate());

        // Add an execution that is valid for the same validator
        String expectedDateExecuted = Instant.now().toString();
        ValidationExecution validationExecution = createValidationExecution(validatorTool, validatorToolVersion1, true);
        validationExecution.setDateExecuted(expectedDateExecuted);
        executions.add(validationExecution);
        validationStatusMetric = new ValidationStatusAggregator().getAggregatedMetricFromAllSubmissions(new ExecutionsRequestBody().validationExecutions(executions));
        assertTrue(validationStatusMetric.isPresent());
        validatorInfo = validationStatusMetric.get().getValidatorTools().get(validatorTool.toString());
        mostRecentValidatorVersion = validatorInfo.getValidatorVersions().stream().filter(validationVersion -> validatorToolVersion1.equals(validationVersion.getName())).findFirst().get();
        assertTrue(mostRecentValidatorVersion.isIsValid(), "Should be true because the latest validation is valid");
        assertEquals(validatorToolVersion1, mostRecentValidatorVersion.getName());
        assertNull(mostRecentValidatorVersion.getErrorMessage());
        assertEquals(2, mostRecentValidatorVersion.getNumberOfRuns());
        assertEquals(100, mostRecentValidatorVersion.getPassingRate());
        assertEquals(expectedDateExecuted, mostRecentValidatorVersion.getDateExecuted()); // Check that this is the most recent ValidatorVersionInfo for this version because it was executed twice
        assertEquals(2, validatorInfo.getValidatorVersions().size(), "There should be 2 ValidatorVersionInfo objects because 2 versions ran");
        assertEquals(3, validatorInfo.getNumberOfRuns());
        assertEquals(66.66666666666666, validatorInfo.getPassingRate());
    }
}
