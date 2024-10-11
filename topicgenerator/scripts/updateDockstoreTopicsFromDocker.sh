#!/usr/bin/env bash

#
# Copyright 2024 OICR and UCSC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

# This script gets entries from Dockstore that are AI topic candidates, generates topics for them, then uploads the topics to Dockstore. It assumes it's
# running in a Docker container built by the /Dockerfile in this repo
# 1. The jar is /home/topic-generator.jar. The Docker image has this.
# 2. There is a /config/topic-generator.config. The host should create the file and make it available to the Docker container
set -o errexit
set -o pipefail
set -o nounset

APP_JAR=/home/topic-generator.jar
CONFIG=/config/topic-generator.config

cd /home

echo "Creating a CSV file of AI topic candidates from Dockstore"
java -jar $APP_JAR -c $CONFIG get-topic-candidates
cat entries_*.csv

echo "Generating topics"
java -jar $APP_JAR -c $CONFIG generate-topics --entries entries_*.csv
cat generated-topics_*.csv

echo "Uploading generated AI topics to Dockstore"
java -jar $APP_JAR -c $CONFIG upload-topics --aiTopics generated-topics_*.csv

