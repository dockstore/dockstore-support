#!/usr/bin/env bash

set -x

TOPIC_GENERATOR_VERSION=${1}

if [ -z "${TOPIC_GENERATOR_VERSION}" ]; then
  echo "Usage: ${0} <topic-generator-version>"
  echo "Example: ${0} 1.16.0-alpha.4"
  exit 1
fi

wget https://artifacts.oicr.on.ca:443/artifactory/collab-release/io/dockstore/topicgenerator/"${TOPIC_GENERATOR_VERSION}"/topicgenerator-"${TOPIC_GENERATOR_VERSION}".jar

echo "Creating a CSV file of AI topic candidates from Dockstore"
java -jar topicgenerator-*.jar get-topic-candidates

echo "Generating topics"
java -jar topicgenerator-*.jar generate-topics

echo "Uploading generated AI topics to Dockstore"
java -jar topicgenerator-*.jar upload-topics --aiTopics generated-topics_*.csv
