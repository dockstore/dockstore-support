
---
**NOTE:**

This `README.md` only shows users how to use the `run-workflows` command. As of April 3, 2023 all other 
commands are no longer working and will require a non-trivial amount of work to get working.

The README for these features can be found here: https://github.com/dockstore/dockstore-support/blob/8775ecadaec8b5fa36d03bc6ba962509ea38ca29/tooltester/README.md

---

# The Tooltester `run-workflows` Command

The purpose of the `run-workflows` command, is to be able to run (in an automated fashion) workflows found on a dockstore site 
(ie. [dockstore.org](https://dockstore.org/) or [qa.dockstore.org](https://qa.dockstore.org/)), and 
- Determine what the end state of the workflow (ie. SUCCESS or EXECUTOR_ERROR)
- Determine how long it takes for a workflow to reach its end state.
- Gather metrics such as CPU usage of the workflow during its run.

All of the above information is then posted to an `S3` container via the [Execution class](https://github.com/dockstore/dockstore/blob/290bc2c68640e82a44bdb8fe58c8629814739dfa/dockstore-webservice/src/main/java/io/dockstore/webservice/core/metrics/Execution.java#L36-L127).

## What is Required to run `run-workflows`

### Set up AGC
You will need to do this twice, once to create a context that runs `WDL` workflows, and one to create a context that
runs `CWL` workflows. Currently, this feature is only tested using Cromwell to run `WDL` workflows and using
Toil to run `CWL` workflows.

First, install AGC using the instructions found [here](https://aws.github.io/amazon-genomics-cli/docs/getting-started/installation/).

Next, activate your `agc` account (you only need to do this once, so if you have done this before you can skip this step).
```
agc account activate
```

Now, you will want to deploy both the context to run `WDL` workflows and the context to run `CWL` workflows. To do this you
need to be in the same directory as `agc-project.yaml`, and run the following commands (each command may take up to 20 minutes to run)
```
agc context deploy wdlContext
```
```
agc context deploy cwlContext
```

Now, run 
```
agc context describe <CONTEXT NAME>
```
on `wdlContext` and `cwlContext`, and get the `WESENDPOINT` value from the result and append `ga4gh/wes/v1` to the end of it and place
the resulting string in the `url` field in `WDLConfigFile` and `CWLConfigFile` respectively.

Now, you will want to add your AWS profile name to both `wdlContext` and `cwlContext` in the `authorization` field. For example,
mine is `fhembroff`.

### Finish writing Config Files
Do the following to both `WDLConfigFile` and `CWLConfigFile`.

In this step you will want to fill in the `server-url` field. This will most likely be either https://qa.dockstore.org/api or https://dockstore.org/api.
You also need to ensure that the `server-url` field is set on your `~/.dockstore/config` file. The `server-url` must be the same across all three files.

Then you will want to get your token from whatever site you chose in the above step, and fill it in, in the `token` field.

You have now successfully set up your config files. They should look something like:
```
token = 000000000000000000000000000000000000000000000
server-url = https://qa.dockstore.org/api
[WES]
url: https://example.execute-api.us-east-1.amazonaws.com/prod/ga4gh/wes/v1
authorization: fhembroff
type: aws
```

### Obtain the Names of the Compute Environments
Each AGC context that we set up before runs on its own ECS Cluster. We need to determine to what the name of each Cluster is in order
to get the metrics for each run from Cloudwatch.

We will need to do the following steps twice, once for our context that runs `WDL` workflows and once for our context that runs `CWL` workflows.

After you determine the name of the ECS Cluster, please save it, as it will be needed in the future.

To determine the cluster name for each context you do the following on the AWS web console:

1. Go to the "Batch" [section](https://us-east-1.console.aws.amazon.com/batch) of the AWS Console
2. Go to the "Compute Environment" [menu](https://us-east-1.console.aws.amazon.com/batch/home?region=us-east-1#console-settings/compute-environments) of the Batch section of the AWS console.
3. Find the Batch Compute Environment associated with your context. If you click on each compute environment and look under tags, you will see the name you chose for the context when you set it up. As an example, the batch compute environment for one of my contexts is `BatchTaskBatchComputeEnv-0FxjUpJuXVowThgf`.
4. Go to the "ECS" section of the AWS console
5. Go to the "Clusters" [menu](https://us-east-1.console.aws.amazon.com/ecs/v2/clusters?region=us-east-1)
6. Find the ECS cluster that starts with your Batch Computer Environment that you identified before. For example, in my case, the ECS cluster is called, `BatchTaskBatchComputeEnv-0FxjUpJuXVowThgf_Batch_2d94d28c-ccaa-3dd5-865c-faad50e542eb`.

### Workflows
In order to run this command, you of course need workflows to run it on. You can add workflows for the `run-workflows` command to run [here](https://github.com/dockstore/dockstore-support/blob/8775ecadaec8b5fa36d03bc6ba962509ea38ca29/tooltester/src/main/java/io/dockstore/tooltester/runWorkflow/WorkflowList.java#L48-L55).

When you add workflows, you can either have them run using the test parameter file found on the appropriate site, such as what is done [here](https://github.com/dockstore/dockstore-support/blob/8775ecadaec8b5fa36d03bc6ba962509ea38ca29/tooltester/src/main/java/io/dockstore/tooltester/runWorkflow/WorkflowList.java#L49). 
You can also have them run using a test parameter file found locally. Such as what is done [here](https://github.com/dockstore/dockstore-support/blob/8775ecadaec8b5fa36d03bc6ba962509ea38ca29/tooltester/src/main/java/io/dockstore/tooltester/runWorkflow/WorkflowList.java#L48).

## Running the `run-workflows` command
To do this you must first compile the project with the following command,
```
mvn clean install -DskipTests
```

Then you simply run (assuming you are in the directory this file is located in)
```
java -jar target/tooltester-0.1-alpha.0-SNAPSHOT.jar run-workflows --CWL-config-file-path CWLConfigFile --WDL-config-file-path WDLConfigFile  --CWL-cluster-name <Name of CWL Cluster Identified Earlier>  --WDL-cluster-name <Name of WDL Cluster Identified Earlier>
```


## Important Notes
- Currently, not all `WDL` workflows generate an ECS task when ran (this is required for cloudwatch metrics) and this means 
that for some `WDL` workflows, we are unable to gather metrics that rely on Cloudwatch (such as CPU usage), but we can still gather their end state and run time. It is not entirely known as to why some `WDL` workflows do
not create ECS tasks, but it is always consistent (ie. if a `WDL` workflow doesn't create an ECS task on one run, it will never create an ECS task, and vice versa).
- Any file referenced in a `WDL` workflow **must** have an `S3` address.

