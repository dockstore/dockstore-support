# Topic Generator

This is a Java program that generates topics for public Dockstore entries and puts the results into the webservice using OpenAI's gpt-3.5-turbo-16k AI model.

The [entries.csv](entries.csv) file contains the TRS ID and default versions of public Dockstore entries to generate topics for. The [results](results) directory contains the generated topics for those entries from running the topic generator. 

## Setup

### Configuration file

Create a configuration file like the following. A template `metrics-aggregator.config` file can be found [here](templates/topic-generator.config).

```
[dockstore]
server-url: <Dockstore server url>

[ai]
openai-api-key: <OpenAI API key>
```

**Required:**
- `server-url`: The Dockstore server URL that's used to send API requests to.
    - Examples:
        - `https://qa.dockstore.org/api`
        - `https://staging.dockstore.org/api`
        - `https://dockstore.org/api`
- `openai-api-key`: The OpenAI API key required for using the OpenAI APIs. See https://platform.openai.com/docs/api-reference/authentication for more details.

## Running the program

```
Usage: <main class> [options] [command] [command options]
  Options:
    --help
      Prints help for topicgenerator
  Commands:
    generate-topics      Generate topics for public Dockstore entries using 
            the gpt-3.5-turbo-16k AI model
      Usage: generate-topics [options]
        Options:
          -c, --config
            The config file path.
            Default: ./topic-generator.config
          -e, --entries
            The file path to the CSV file containing the TRS ID, and version 
            name of the entries to generate topics for. The first line of the 
            file should contain the CSV fields: trsID,version
            Default: ./entries.csv
          --help
            Prints help for topicgenerator
```

### generate-topics

**Using the default configuration file path and entries input path:**

`java -jar target/topicgenerator-*-SNAPSHOT.jar generate-topics`

**Using a custom configuration file path and custom entries input path:**

`java -jar target/topicgenerator-*-SNAPSHOT.jar generate-topics --config my-custom-config --entries my-custom-entries.csv`

