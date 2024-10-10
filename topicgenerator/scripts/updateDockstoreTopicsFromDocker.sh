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

set -o errexit
set -o pipefail
set -o nounset

APP_JAR=/home/topic-generator.jar

cd /home

echo "Creating a CSV file of AI topic candidates from Dockstore"
java -jar $APP_JAR get-topic-candidates

echo "Generating topics"
java -jar $APP_JAR generate-topics --entries entries_*.csv

echo "Uploading generated AI topics to Dockstore"
java -jar $APP_JAR upload-topics --aiTopics generated-topics_*.csv

cd ..
echo "View results in directory ${TOPIC_GENERATOR_DIR}"