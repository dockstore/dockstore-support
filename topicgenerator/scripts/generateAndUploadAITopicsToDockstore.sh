#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset

# This script gets entries from Dockstore that are AI topic candidates, generates topics for them, then uploads the topics to Dockstore.

TOPIC_GENERATOR_VERSION=${1}
TOPIC_GENERATOR_CONFIG_PATH=${2}
MAX_CANDIDATES="${3:-}"

if [ -z "${TOPIC_GENERATOR_VERSION}" ] || [ -z "${TOPIC_GENERATOR_CONFIG_PATH}" ]; then
  echo "Usage: ${0} <topic-generator-version> <topic-generator-config-path> <optional-max-number-of-candidates>"
  echo "Example: ${0} 1.16.0-alpha.4 ./topic-generator.config"
  exit 1
fi

TOPIC_GENERATOR_CONFIG_ABS_PATH=$(readlink -f ${TOPIC_GENERATOR_CONFIG_PATH})

LONG_DATE=`date +%Y-%m-%dT%H-%M-%S%z`
TOPIC_GENERATOR_DIR=topicgenerator-"${LONG_DATE}"
mkdir "${TOPIC_GENERATOR_DIR}"
cd "${TOPIC_GENERATOR_DIR}"

TOPIC_GENERATOR_JAR="/home/ktran/dev/dockstore-support/topicgenerator/target/topicgenerator-1.16.0-SNAPSHOT.jar" #topicgenerator-"${TOPIC_GENERATOR_VERSION}".jar
#wget https://artifacts.oicr.on.ca:443/artifactory/collab-release/io/dockstore/topicgenerator/"${TOPIC_GENERATOR_VERSION}"/"${TOPIC_GENERATOR_JAR}"

if [ -z "${MAX_CANDIDATES}" ]; then
  echo "Generating topics for all AI topic candidates from Dockstore"
  java -jar "${TOPIC_GENERATOR_JAR}" -c "${TOPIC_GENERATOR_CONFIG_ABS_PATH}" generate-topics
else
  echo "Generating topics for a maximum of ${MAX_CANDIDATES} AI topic candidates from Dockstore"
  java -jar "${TOPIC_GENERATOR_JAR}" -c "${TOPIC_GENERATOR_CONFIG_ABS_PATH}" generate-topics --max "${MAX_CANDIDATES}"
fi

echo "Uploading generated AI topics to Dockstore"
java -jar "${TOPIC_GENERATOR_JAR}" -c "${TOPIC_GENERATOR_CONFIG_ABS_PATH}" upload-topics --aiTopics generated-topics_*.csv

cd ..
echo "View results in directory ${TOPIC_GENERATOR_DIR}"