
---
**NOTE:**

This `README.md` only shows users how to use the `run-workflows-through-wes` command. As of April 3, 2023 all other 
commands are no longer working and will require a non-trivial amount of work to get working.

The README for these features can be found here: https://github.com/dockstore/dockstore-support/blob/8775ecadaec8b5fa36d03bc6ba962509ea38ca29/tooltester/README.md

---

# The Tooltester `run-workflows-through-wes` Command

The purpose of the `run-workflows-through-wes` command, is to be able to run (in an automated fashion) workflows found on a Dockstore site 
(ie. [dockstore.org](https://dockstore.org/) or [qa.dockstore.org](https://qa.dockstore.org/)), and 
- Determine what the end state of the workflow (ie. SUCCESS or EXECUTOR_ERROR)
- Determine how long it takes for a workflow to reach its end state.
- Gather metrics such as CPU usage of the workflow during its run.

All of the above information is then posted to an `S3` container via the [RunExecution class](https://github.com/dockstore/dockstore/blob/develop/dockstore-webservice/src/main/java/io/dockstore/webservice/core/metrics/RunExecution.java).

## What is Required to run `run-workflows-through-wes`
To run the `run-workflows-through-wes` we have to set a few things up first. This can either be done manually or with the `setUpTooltester.sh` script.
It is **highly recommended** that you use the script.

## How to use the Setup Script
First, ensure that your `agc-project.yaml` file is in the same directory as `setUpTooltester.sh`.

Then, install AGC using the instructions found [here](https://aws.github.io/amazon-genomics-cli/docs/getting-started/installation/).

Next, activate your `agc` account (you only need to do this once, so if you have done this before you can skip this step).
```
agc account activate
```


Next ensure that your `agc-project.yaml` contains **exactly** two contexts named `wdlContext` and `cwlContext`, which are set up to run `WDL` and `CWL` workflows respectively.
Next we need to deploy these contexts, this can be done with the command (if you have already deployed these contexts, you can skip this step),
```
agc context deploy wdlContext cwlContext
```
Note: the above command may take up to 20 minutes to run

Note: All contexts on AGC are user-scoped, so having a context with the same name as a context created by someone else is not a problem.

Now you are ready to use the configuration script, simply run:
```
./setUpTooltester.sh
```
and follow the prompts.

This script will create the configuration file `tooltesterConfig.yml` for you.

<details>
<summary>How to set up tooltester for the run-workflows-through-wes command manually (NOT RECOMMENDED)</summary>

### Set up AGC
You will need to do this twice, once to create a context that runs `WDL` workflows, and once to create a context that
runs `CWL` workflows. Currently, this feature is only tested using Cromwell to run `WDL` workflows and using
Toil to run `CWL` workflows.

First, install AGC using the instructions found [here](https://aws.github.io/amazon-genomics-cli/docs/getting-started/installation/).

Next, activate your `agc` account (you only need to do this once, so if you have done this before you can skip this step).
```
agc account activate
```

Now, you will want to deploy both the context to run `WDL` workflows and the context to run `CWL` workflows. To do this you
need to be in the same directory as `agc-project.yaml`, and run the following commands (each command may take up to 20 minutes to run).
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
the resulting string in the `WDL-WES-URL` and `CWL-WES-URL` field in `tooltesterConfig.yml` respectively.

NOTE: All contexts on AGC are user-scoped, so having a context with the same name as a context created by someone else is not a problem.

Now, you will want to add your AWS profile name to the `AWS-AUTHORIZATION` field in `tooltesterConfig.yml`. For example,
mine is `fhembroff`.

### Add Server Url and Token to `tooltesterConfig.yml`

In this step you must fill in the `TOKEN` and `SERVER-URL` in `tooltesterConfig.yml`. The `SERVER-URL` will most likely
either be https://qa.dockstore.org or https://dockstore.org. The token is obtained from whatever dockstore site you chose
for the `SERVER-URL` and must have either admin or curator permissions, as that is what is required by one of the endpoints that we use.




### Obtain the Names of the Compute Environments and Turn On Container Insights
Each AGC context that we set up before runs on its own ECS Cluster. We need to determine what the name of each Cluster is in order
to get the metrics for each run from Cloudwatch. We also need to turn Container Insights on for each ECS cluster, 
to ensure that Cloudwatch collects the needed statistics.

We will need to do the following steps twice, once for our context that runs `WDL` workflows and once for our context that runs `CWL` workflows.

After you determine the name of the ECS Cluster, please add it to `tooltesterConfig.yml` under either `WDL-ECS-CLUSTER` or `CWL-ECS-CLUSTER`.

To determine the cluster name for each context you do the following on the AWS web console:

1. Go to the "Batch" [section](https://us-east-1.console.aws.amazon.com/batch) of the AWS Console
2. Go to the "Compute Environment" [menu](https://us-east-1.console.aws.amazon.com/batch/home?region=us-east-1#console-settings/compute-environments) of the Batch section of the AWS console.
3. Find the Batch Compute Environment associated with your context. If you click on each compute environment and look under tags, you will see the name you chose for the context when you set it up. As an example, the batch compute environment for one of my contexts is `BatchTaskBatchComputeEnv-0FxjUpJuXVowThgf`.
4. Turn on Container Insights for the Batch Compute Environment using the instructions found [here](https://docs.aws.amazon.com/batch/latest/userguide/cloudwatch-container-insights.html).
5. Go to the "ECS" section of the AWS console
6. Go to the "Clusters" [menu](https://us-east-1.console.aws.amazon.com/ecs/v2/clusters?region=us-east-1)
7. Find the ECS cluster that starts with your Batch Computer Environment that you identified before. For example, in my case, the ECS cluster is called, `BatchTaskBatchComputeEnv-0FxjUpJuXVowThgf_Batch_2d94d28c-ccaa-3dd5-865c-faad50e542eb`.


### You have now set up `tooltesterConfig.yml`
It should look something like this,
```
SERVER-URL: https://qa.dockstore.org/api
TOKEN: 000000000000000000000000000000000000000000
AWS-AUTHORIZATION: fhembroff
WDL-WES-URL: https://example1.execute-api.us-east-1.amazonaws.com/prod/ga4gh/wes/v1
CWL-WES-URL: https://example2.execute-api.us-east-1.amazonaws.com/prod/ga4gh/wes/v1
WDL-ECS-CLUSTER: BatchTaskBatchComputeEnv-0000000000000000_Batch_00000000-0000-0000-0000-000000000000
CWL-ECS-CLUSTER: BatchTaskBatchComputeEnv-1111111111111111_Batch_11111111-1111-1111-1111-111111111111
```

</details>

### Workflows
In order to run this command, you of course need workflows to run it on. You can add workflows for the `run-workflows-through-wes` command to run [here](https://github.com/dockstore/dockstore-support/blob/develop/tooltester/src/main/java/io/dockstore/tooltester/runWorkflow/WorkflowList.java).

When you add workflows, you can either have them run using the test parameter file found on the appropriate site,
or you can also have them run using a test parameter file found locally. 

## Running the `run-workflows-through-wes` command
To do this you must first compile the project with the following command,
```
./mvnw clean install -DskipTests
```

Then you simply run (assuming you are in the directory this file is located in)
```
java -jar target/tooltester-0.1-alpha.0-SNAPSHOT.jar run-workflows-through-wes
```
Note: Running this command can take up to 2 hours depending on the workflows that are being run.

Note: You can also specify the path to the tooltester config file with the `--config-file-path`, by default the tooltester config file is `./tooltesterConfig.yml`.

## Clean Up Instructions
After you are done using the `run-workflows-through-wes` command, you will want to destroy any contexts that you have created.
This is because unneeded contexts can lead to unnecessary costs. You can destroy your contexts with the following command
(please run this command in the same directory as `agc-project.yaml`):
```
agc context destroy --all
```
Note: This command may take up to 20 minutes to run

## Important Notes
- Currently, not all `WDL` workflows generate an ECS task when ran (this is required for cloudwatch metrics) and this means 
that for some `WDL` workflows, we are unable to gather metrics that rely on Cloudwatch (such as CPU usage), but we can still gather their end state and run time. It is not entirely known as to why some `WDL` workflows do
not create ECS tasks, but it is always consistent (ie. if a `WDL` workflow doesn't create an ECS task on one run, it will never create an ECS task, and vice versa).
- Any file referenced in a `WDL` workflow **must** have an `S3` address.
- You will want to set your aws credentials both as environment variables and in the file `~/.aws/credentials`. You can obtain these credentials using [this tutorial](https://wiki.oicr.on.ca/display/DOC/Access+AWS+CLI+with+MFA).

