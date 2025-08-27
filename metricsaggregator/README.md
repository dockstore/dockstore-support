# Metrics Aggregator

This is a Java program that aggregates metrics from S3 and puts the results into the webservice. It also can submit workflow validation
information to Dockstore.

## Setup

### Configuration file

Create a configuration file like the following. A template `metrics-aggregator.config` file can be found [here](templates/metrics-aggregator.config).

```
[dockstore]
server-url: <Dockstore server url>
token: <Dockstore token>

[s3]
bucketName: <S3 metrics bucket name>
endpointOverride: <Optional S3 endpoint override>

[athena]
workgroup: <Athena workgroup name>
```
**Required:**
- `server-url`: The Dockstore server URL that's used to send API requests to.
  - Examples:
    - `https://qa.dockstore.org/api`
    - `https://staging.dockstore.org/api`
    - `https://dockstore.org/api`
- `token`: The Dockstore token of a Dockstore user. This user must be an admin or curator in order to be able to post aggregated metrics to Dockstore.
- `bucketName`: The S3 bucket name storing metrics data. This is the bucket that the metrics aggregator will go through in order
  to aggregate metrics.
- `workgroup`: The Athena workgroup name that Athena queries are executed in.

**Optional:**
- `endpointOverride`: Endpoint override to use when creating the S3 clients. This is typically only used for local testing so that a LocalStack endpoint 
override can be used. Omit this key completely if you're running the metrics aggregator against non-local Dockstore environments like prod, staging, and QA. View the [template](templates/metrics-aggregator.config) for an example of a config file without this key.

Note that if the configuration file path is not passed as an argument via `--config` or `-c`, then the default location is set to `./metrics-aggregator.config`. 

### AWS credentials

This program requires AWS credentials that have permissions to read the S3 metrics bucket. There are several ways that this can be provided.
Read [this](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain) for the default credential provider chain.

## Running the program

```
Usage: <main class> [options] [command] [command options]
  Options:
    --help
      Prints help for metricsaggregator
  Commands:
    aggregate-metrics      Aggregate metrics in S3
      Usage: aggregate-metrics [options]
        Options:
          --allS3
            Aggregate all executions in S3, even if they have been aggregated 
            before 
            Default: false
          -c, --config
            The config file path.
            Default: ./metrics-aggregator.config
          --dryRun
            Do a dry run by printing out the S3 directories that will be 
            aggregated 
            Default: false
          --help
            Prints help for metricsaggregator
          --trsIds
            Aggregate metrics for the tools specified by their TRS IDs

    submit-validation-data      Formats workflow validation data specified in 
            a file then submits it to Dockstore
      Usage: submit-validation-data [options]
        Options:
          -c, --config
            The config file path.
            Default: ./metrics-aggregator.config
        * -d, --data
            The file path to the CSV file containing the TRS ID, version name, 
            isValid boolean value, and date executed in ISO 8601 UTC date 
            format of the workflows that were validated by the validator 
            specified. The first line of the file should contain the CSV 
            fields: trsID,versionName,isValid,dateExecuted
          -id, --executionId
            The execution ID to use for each validation execution. Assumes 
            that each validation in the file is performed on unique workflows 
            and workflow versions.
          --help
            Prints help for metricsaggregator
        * -p, --platform
            The platform that the workflow was validated on
            Possible Values: [GALAXY, TERRA, DNA_STACK, DNA_NEXUS, CGC, NHLBI_BIODATA_CATALYST, ANVIL, CAVATICA, NEXTFLOW_TOWER, ELWAZI, AGC, OTHER, ALL]
        * -v, --validator
            The validator tool used to validate the workflows
            Possible Values: [MINIWDL, WOMTOOL, CWLTOOL, NF_VALIDATION, OTHER]
        * -vv, --validatorVersion
            The version of the validator tool used to validate the workflows

    submit-terra-metrics      Submits workflow metrics provided by Terra via a 
            CSV file to Dockstore
      Usage: submit-terra-metrics [options]
        Options:
          -c, --config
            The config file path.
            Default: ./metrics-aggregator.config
        * -d, --data
            The file path to the CSV file containing workflow metrics from 
            Terra. The first line of the file should contain the CSV fields: workflow_id,status,workflow_start,workflow_end,workflow_runtime_minutes,source_url
          -de, --description
            Optional description about the metrics to include when submitting 
            metrics to Dockstore
          --help
            Prints help for metricsaggregator
          -r, --recordSkipped
            Record skipped executions and the reason skipped to a CSV file
            Default: false
```

### aggregate-metrics

**Using the default configuration file path:**

`java -jar target/metricsaggregator-*-SNAPSHOT.jar aggregate-metrics`

**Using a custom configuration file path:**

`java -jar target/metricsaggregator-*-SNAPSHOT.jar aggregate-metrics --config my-custom-config`

### submit-validation-data

The following is an example command that submits validation data for a file that contains the names of workflow versions that validated successfully
with miniwdl on DNAstack:

```
java -jar target/metricsaggregator-*-SNAPSHOT.jar submit-validation-data --config my-custom-config \
--data <path-to-my-data-file> --validator MINIWDL --validatorVersion 1.0 --platform DNA_STACK --executionId a02075d9-092a-4fe7-9f83-4abf11de3dc9
```

After running this command, you will want to run the `aggregate-metrics` command to aggregate the new validation data submitted.

### submit-terra-metrics

The following is an example command that submits metrics from a CSV file that Terra provided, recording the metrics that were skipped into an output CSV file.

```
java -jar target/metricsaggregator-*-SNAPSHOT.jar submit-terra-metrics --config my-custom-config \
--data <path-to-terra-metrics-csv-file> --recordSkipped
```

After running this command, you will want to run the `aggregate-metrics` command to aggregate the new Terra metrics submitted.

## AWS Infrastructure Required
See the [AthenaStack in dockstore-deploy](https://github.com/dockstore/dockstore-deploy/blob/develop/cdk-templates/athena/src/main/java/io/dockstore/athena/AthenaStack.java) 
for the AWS resources required to run the metrics aggregator.

This program requires:
- An S3 bucket that contains the submitted metrics and an Athena workgroup
- An S3 bucket to store results from running Athena queries (used as the S3 output location in the Athena workgroup below).
- An Athena workgroup which isolates queries from other queries in the account. The S3 output location is specified in this workgroup.

The program requires AWS credentials that have permissions to:
- Read the S3 bucket containing the submitted metrics
- Upload query results to the S3 bucket that stores the results (S3 output location), execute queries in Athena (`athena:`), and create databases and tables in AWS Glue (`glue:`).
  - See [dockstore-deploy](https://github.com/dockstore/dockstore-deploy/blob/develop/cdk-templates/stack-utils/src/main/java/io/dockstore/stackutils/PolicyConstants.java#L61) for a detailed list of the permissions.
