#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset

# This script gets entries from Dockstore that are AI topic candidates, generates topics for them, then uploads the topics to Dockstore.

TOPIC_GENERATOR_VERSION=${1}
TOPIC_GENERATOR_CONFIG_PATH=${2}

if [ -z "${TOPIC_GENERATOR_VERSION}"] || [ -z "${TOPIC_GENERATOR_CONFIG_PATH}" ]; then
  echo "Usage: ${0} <topic-generator-version> <topic-generator-config-path>"
  echo "Example: ${0} 1.16.0-alpha.4 ./topic-generator.config"
  exit 1
fi

TOPIC_GENERATOR_CONFIG_ABS_PATH=$(readlink -f ${TOPIC_GENERATOR_CONFIG_PATH})

LONG_DATE=`date +%Y-%m-%dT%H-%M-%S%z`
TOPIC_GENERATOR_DIR=topicgenerator-"${LONG_DATE}"
mkdir "${TOPIC_GENERATOR_DIR}"
cd "${TOPIC_GENERATOR_DIR}"

TOPIC_GENERATOR_JAR=topicgenerator-"${TOPIC_GENERATOR_VERSION}".jar
wget https://artifacts.oicr.on.ca:443/artifactory/collab-release/io/dockstore/topicgenerator/"${TOPIC_GENERATOR_VERSION}"/"${TOPIC_GENERATOR_JAR}"

echo "Creating a CSV file of AI topic candidates from Dockstore"
java -jar "${TOPIC_GENERATOR_JAR}" -c "${TOPIC_GENERATOR_CONFIG_ABS_PATH}" get-topic-candidates

echo "Generating topics"
java -jar "${TOPIC_GENERATOR_JAR}" -c "${TOPIC_GENERATOR_CONFIG_ABS_PATH}" generate-topics --entries entries_*.csv

echo "Uploading generated AI topics to Dockstore"
java -jar "${TOPIC_GENERATOR_JAR}" -c "${TOPIC_GENERATOR_CONFIG_ABS_PATH}" upload-topics --aiTopics generated-topics_*.csv

cd ..
echo "View results in directory ${TOPIC_GENERATOR_DIR}"