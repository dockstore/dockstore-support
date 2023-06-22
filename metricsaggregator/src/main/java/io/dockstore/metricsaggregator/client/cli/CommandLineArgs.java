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

package io.dockstore.metricsaggregator.client.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.dockstore.common.Partner;
import io.dockstore.openapi.client.model.ValidationExecution.ValidatorToolEnum;
import java.io.File;

public class CommandLineArgs {
    @Parameter(names = "--help", description = "Prints help for metricsaggregator", help = true)
    private boolean help = false;

    public boolean isHelp() {
        return help;
    }

    @Parameters(commandNames = { "aggregate-metrics" }, commandDescription = "Aggregate metrics in S3")
    public static class AggregateMetricsCommand extends CommandLineArgs {
        @Parameter(names = {"-c", "--config"}, description = "The config file path.")
        private File config = new File("./" + MetricsAggregatorClient.CONFIG_FILE_NAME);

        public File getConfig() {
            return config;
        }
    }

    @Parameters(commandNames = { "submit-validation-data" }, commandDescription = "Formats workflow validation data specified in a file then submits it to Dockstore")
    public static class SubmitValidationData extends  CommandLineArgs {
        @Parameter(names = {"-c", "--config"}, description = "The config file path.")
        private File config = new File("./" + MetricsAggregatorClient.CONFIG_FILE_NAME);

        @Parameter(names = {"-v", "--validator"}, description = "The validator tool used to validate the workflows", required = true)
        private ValidatorToolEnum validator;

        @Parameter(names = {"-vv", "--validatorVersion"}, description = "The version of the validator tool used to validate the workflows", required = true)
        private String validatorVersion;

        @Parameter(names = {"-d", "--data"}, description = "The file path to the CSV file containing the TRS ID, version name, isValid boolean value, and date executed in ISO 8601 UTC date format of the workflows that were validated by the validator specified. The first line of the file should contain the CSV fields: trsID,versionName,isValid,dateExecuted", required = true)
        private String dataFilePath;

        @Parameter(names = {"-p", "--platform"}, description = "The platform that the workflow was validated on", required = true)
        private Partner platform;

        public File getConfig() {
            return config;
        }

        public ValidatorToolEnum getValidator() {
            return validator;
        }

        public String getValidatorVersion() {
            return validatorVersion;
        }

        public String getDataFilePath() {
            return dataFilePath;
        }

        public Partner getPlatform() {
            return platform;
        }
    }
}
