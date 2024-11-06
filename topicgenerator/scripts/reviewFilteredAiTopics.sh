#!/bin/bash

set -x -u

SUPPORT_VERSION=${1}
CONFIG_PATH=${2}
S3_URI=${3}

wget https://artifacts.oicr.on.ca:443/artifactory/collab-release/io/dockstore/topicgenerator/${SUPPORT_VERSION}/topicgenerator-${SUPPORT_VERSION}.jar

java -jar topicgenerator-${SUPPORT_VERSION}.jar -c ${CONFIG_PATH} upload-topics -ai ${S3_URI} --review