/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.metricsaggregator.common;

import io.dockstore.metricsaggregator.MetricsAggregatorConfig;
import io.dockstore.metricsaggregator.client.cli.MetricsAggregatorClient;
import io.dockstore.openapi.client.model.RunExecution;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.util.Optional;
import org.apache.commons.configuration2.INIConfiguration;

public final class TestUtilities {
    public static final String CONFIG_FILE_PATH = ResourceHelpers.resourceFilePath("metrics-aggregator.config");
    public static final MetricsAggregatorConfig METRICS_AGGREGATOR_CONFIG = getMetricsConfig();
    public static final String BUCKET_NAME = METRICS_AGGREGATOR_CONFIG.getS3Bucket();
    public static final String ENDPOINT_OVERRIDE = METRICS_AGGREGATOR_CONFIG.getS3EndpointOverride();

    private TestUtilities() {
    }

    public static RunExecution createRunExecution(RunExecution.ExecutionStatusEnum executionStatus, String executionTime, Integer cpuRequirements, Double memoryRequirementsGB, Double costUSD, String region) {
        return new RunExecution()
                .executionStatus(executionStatus)
                .executionTime(executionTime)
                .cpuRequirements(cpuRequirements)
                .memoryRequirementsGB(memoryRequirementsGB)
                .costUSD(costUSD)
                .region(region);
    }

    public static MetricsAggregatorConfig getMetricsConfig() {
        Optional<INIConfiguration> iniConfig = MetricsAggregatorClient.getConfiguration(new File(CONFIG_FILE_PATH));
        if (iniConfig.isEmpty()) {
            throw new RuntimeException("Unable to get config file");
        }

        MetricsAggregatorConfig config = new MetricsAggregatorConfig(iniConfig.get());
        return config;
    }
}
