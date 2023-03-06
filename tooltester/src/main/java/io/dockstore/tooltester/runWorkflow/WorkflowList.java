package io.dockstore.tooltester.runWorkflow;

import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;

import java.util.List;

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
                        new WorkflowRunner("github.com/manning-lab/vcfToGds", "main", "test-parameter-files/vcfToGds-test-parameter-file.json", extendedGa4GhApi)
                );
    }

    public List<WorkflowRunner> getWorkflowsToRun() {
        return workflowsToTest;
    }

}
