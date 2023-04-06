# Metrics Aggregator

This is a Java program that aggregates metrics from S3 and puts the results into the webservice. It also can submit workflow validation
information to Dockstore.

## Setup

### Configuration file

Create a configuration file like the following:

```
[dockstore]
server-url: <Dockstore server url>
token: <Dockstore token>

[s3]
bucketName: <S3 metrics bucket name>
endpointOverride: <Optional S3 endpoint override>
```
**Required:**
- `server-url`: The Dockstore server URL that's used to send API requests to.
- `token`: The Dockstore token of a Dockstore user. This user must be an admin or curator in order to be able to post aggregated metrics to Dockstore.
- `bucketName`: The S3 bucket name storing metrics data. This is the bucket that the metrics aggregator will go through in order
  to aggregate metrics.

**Optional:**
- `endpointOverride`: Endpoint override to use when creating the S3 clients. This is typically only used for local testing so that a LocalStack endpoint 
override can be used.

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
          -c, --config
            The config file path.
            Default: ./metrics-aggregator.config
          --help
            Prints help for metricsaggregator

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
          --help
            Prints help for metricsaggregator
        * -p, --platform
            The platform that the workflow was validated on
            Possible Values: [GALAXY, TERRA, DNA_STACK, DNA_NEXUS, CGC, NHLBI_BIODATA_CATALYST, ANVIL, CAVATICA, NEXTFLOW_TOWER, ELWAZI, AGC, OTHER]
        * -v, --validator
            The validator tool used to validate the workflows
            Possible Values: [MINIWDL, WOMTOOL, CWLTOOL, NF_VALIDATION, OTHER]
        * -vv, --validatorVersion
            The version of the validator tool used to validate the workflows
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
--data <path-to-my-data-file> --validator MINIWDL --validatorVersion 1.0 --platform DNA_STACK 
```