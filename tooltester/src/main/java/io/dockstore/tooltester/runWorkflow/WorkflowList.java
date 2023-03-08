/*
 *    Copyright 2023
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.tooltester.runWorkflow;

import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;

import java.util.List;

/**
 * Contains a list of workflows that will be tested when "run-workflows" is ran
 */
public class WorkflowList {

    private Ga4Ghv20Api ga4Ghv20Api;
    private ExtendedGa4GhApi extendedGa4GhApi;
    public WorkflowList(Ga4Ghv20Api ga4Ghv20Api, ExtendedGa4GhApi extendedGa4GhApi) throws InterruptedException {
        this.ga4Ghv20Api = ga4Ghv20Api;
        this.extendedGa4GhApi = extendedGa4GhApi;
        this.workflowsToTest = getDefaultTests();
    }
    private final List<WorkflowRunner> workflowsToTest;

    private List<WorkflowRunner> getDefaultTests() throws InterruptedException {
        return List.of(new WorkflowRunner("github.com/dockstore-testing/wes-testing/agc-fastq-read-counts", "main", "test-parameter-files/agc-fastq-read-counts-test-parameter-file.json", extendedGa4GhApi),
                        new WorkflowRunner("github.com/dockstore-testing/wes-testing/agc-fastq-read-counts", "main", "agc-examples/fastq/input.json", ga4Ghv20Api, extendedGa4GhApi),
                        new WorkflowRunner("github.com/gatk-workflows/seq-format-conversion/BAM-to-Unmapped-BAM", "3.0.0", "test-parameter-files/BAM-to-Unmapped-BAM-test-parameter-file.json", extendedGa4GhApi),
                        new WorkflowRunner("github.com/manning-lab/vcfToGds", "main", "test-parameter-files/vcfToGds-test-parameter-file.json", extendedGa4GhApi),
                        new WorkflowRunner("github.com/DataBiosphere/analysis_pipeline_WDL/assocation-aggregate-wdl", "v7.1.1", "test-parameter-files/assocation-aggregate-wdl-test-parameter-file.json", extendedGa4GhApi)
                );
    }

    public List<WorkflowRunner> getWorkflowsToRun() {
        return workflowsToTest;
    }

}
