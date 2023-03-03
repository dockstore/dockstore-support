package io.dockstore.tooltester.runWorkflow;

import io.dockstore.openapi.client.api.Ga4Ghv20Api;

import java.util.List;

public class WorkflowList {

    private Ga4Ghv20Api ga4Ghv20Api;
    public WorkflowList(Ga4Ghv20Api ga4Ghv20Api) throws InterruptedException {
        this.ga4Ghv20Api = ga4Ghv20Api;
        this.workflowsToTest = getDefaultTests();
    }
    private final List<WorkflowRunner> workflowsToTest;

    private List<WorkflowRunner> getDefaultTests() throws InterruptedException {
        return List.of(new WorkflowRunner("github.com/dockstore-testing/wes-testing/agc-fastq-read-counts", "main", "test-parameter-files/agc-fastq-read-counts-test-parameter-file.json"),
                        new WorkflowRunner("github.com/dockstore-testing/wes-testing/agc-fastq-read-counts", "main", "/agc-examples/fastq/input.json", ga4Ghv20Api),
                        new WorkflowRunner("github.com/gatk-workflows/seq-format-conversion/BAM-to-Unmapped-BAM", "3.0.0", "test-parameter-files/BAM-to-Unmapped-BAM-test-parameter-file.json"),
                        new WorkflowRunner("github.com/manning-lab/vcfToGds", "main", "test-parameter-files/vcfToGds-test-parameter-file.json")
                );
    }

    public List<WorkflowRunner> getWorkflowsToRun() {
        return workflowsToTest;
    }

}
