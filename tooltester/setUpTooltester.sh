#!/bin/bash

#  Copyright 2023
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# This script fills in tooltesterConfig.yml for you

AGC_CONFIG_FILE="agc-project.yaml"
WDL_CONTEXT_NAME="wdlContext"
CWL_CONTEXT_NAME="cwlContext"

echo "Welcome to the Tooltester Configuration Script!"
echo "NOTE: Your AGC contexts $WDL_CONTEXT_NAME and $CWL_CONTEXT_NAME must have already been deployed"
echo "      This script must be run in the same directory as $AGC_CONFIG_FILE"
echo ""

TOOLTESTER_CONFIG_FILE_NAME="tooltesterConfig.yml"

if [[ ! -f $TOOLTESTER_CONFIG_FILE_NAME ]]
then
  > $TOOLTESTER_CONFIG_FILE_NAME
fi

echo "Please enter your dockstore server url (ie. https://qa.dockstore.org/api or https://dockstore.org/api)"
read -r SERVER_URL
yq -i ".SERVER-URL = \"$SERVER_URL\"" $TOOLTESTER_CONFIG_FILE_NAME

echo "Please enter your dockstore token"
echo "Note: This token must have admin or curator access to $SERVER_URL"
read -r TOKEN
yq -i ".TOKEN = \"$TOKEN\"" $TOOLTESTER_CONFIG_FILE_NAME

echo "Please enter your AWS profile name (ie. fhembroff)"
read -r AWS_PROFILE
yq -i ".AWS-AUTHORIZATION = \"$AWS_PROFILE\"" $TOOLTESTER_CONFIG_FILE_NAME

END_OF_WES_URL="ga4gh/wes/v1"

# SET WDL WES Endpoint URL
WDL_WES_ENDPOINT_URL=$(agc context describe $WDL_CONTEXT_NAME --format json 2> /dev/null | jq .WesEndpoint.Url | tr -d '"')
yq -i ".WDL-WES-URL = \"$WDL_WES_ENDPOINT_URL$END_OF_WES_URL\"" $TOOLTESTER_CONFIG_FILE_NAME

# SET CWL WES Endpoint URL
CWL_WES_ENDPOINT_URL=$(agc context describe $CWL_CONTEXT_NAME --format json 2> /dev/null | jq .WesEndpoint.Url | tr -d '"')
yq -i ".CWL-WES-URL = \"$CWL_WES_ENDPOINT_URL$END_OF_WES_URL\"" $TOOLTESTER_CONFIG_FILE_NAME


if [[ ! -f "$AGC_CONFIG_FILE" ]]
then
  echo "No file named '$AGC_CONFIG_FILE' in current directory"
  echo "Please run this script in the same directory that contains '$AGC_CONFIG_FILE'"
  exit 1
fi

CONTEXT_NAME=""

Determine_compute_environment_names_and_set_variables() {
  local NUMBER_OF_ENGINES_IN_CONTEXT=$(yq ".contexts.$CONTEXT_NAME.engines | length" "$AGC_CONFIG_FILE" | grep -cv ^$)
  if [[ $NUMBER_OF_ENGINES_IN_CONTEXT -eq 0 ]]
  then
    echo "You do not have any engines listed for $CONTEXT_NAME, you must have exactly one engine for this context"
    exit 1
  fi

  if [[ $NUMBER_OF_ENGINES_IN_CONTEXT -gt 1 ]]
  then
    echo "You have more than one engine listed for $CONTEXT_NAME, you must have exactly one engine for this context"
    exit 1
  fi

  local ENGINE_TYPE=$(yq ".contexts.$CONTEXT_NAME.engines[0].type" "$AGC_CONFIG_FILE")
  local ENGINE_PROVIDER=$(yq ".contexts.$CONTEXT_NAME.engines[0].engine" "$AGC_CONFIG_FILE")
  local PROJECT_NAME=$(yq ".name" "$AGC_CONFIG_FILE")

  # Get Batch Compute Environment Name
  local BATCH_COMPUTE_ENVIRONMENT_NAME=$(aws batch describe-compute-environments --output json | jq ".computeEnvironments[]
  | select((.computeResources.tags.\"agc-engine\" == \"$ENGINE_PROVIDER\")
  and (.computeResources.tags.\"agc-context\" == \"$CONTEXT_NAME\")
  and (.computeResources.tags.\"agc-project\" == \"$PROJECT_NAME\")
  and (.computeResources.tags.\"agc-engine-type\" == \"$ENGINE_TYPE\")) | .computeEnvironmentName")

  local NUMBER_OF_MATCHING_COMPUTE_ENVIRONMENTS=$(echo "$BATCH_COMPUTE_ENVIRONMENT_NAME" | grep -cv ^$)
  if [[ $NUMBER_OF_MATCHING_COMPUTE_ENVIRONMENTS -gt 1 ]]
  then
    echo "The script has detected more than one matching compute environment"
    echo "Please set ECS cluster manually using the instructions in README.md"
    exit 1
  elif [[ $NUMBER_OF_MATCHING_COMPUTE_ENVIRONMENTS -lt 1 ]]
  then
    echo "The script did not detect any matching compute environments"
    echo "Please ensure that you have started the context $CONTEXT_NAME following the instructions in README.md"
    echo "You can also set the ECS cluster manually using the instructions in README.md"
    exit 1
  fi

  local ECS_CLUSTER_ARN=$(aws ecs list-clusters | jq ".clusterArns[] | select(.|test($BATCH_COMPUTE_ENVIRONMENT_NAME))" | tr -d '"')

  local NUMBER_OF_MATCHING_ECS_ARNS=$(echo "$ECS_CLUSTER_ARN" | grep -cv ^$)

  if [[ $NUMBER_OF_MATCHING_ECS_ARNS -gt 1 ]]
  then
    echo "The script has detected more than one matching ECS ARN"
    echo "Please set the ECS cluster manually using instructions in README.md"
    exit 1
  elif [[ $NUMBER_OF_MATCHING_ECS_ARNS -lt 1 ]]
  then
    echo "The script did not detect any matching ECS ARNs"
    echo "Please set the ECS cluster manually using instructions in README.md"
    exit 1
  fi

  # Selecting element 0 in array as ECS_CLUSTER_ARN only contains 1 element (verified above), so the below command will
  # only return 1 result
  ECS_CLUSTER_NAME=$(aws ecs describe-clusters --cluster "$ECS_CLUSTER_ARN" | jq '.clusters[0].clusterName')
  ECS_CLUSTER_NAME_NO_QUOTES=$(echo "$ECS_CLUSTER_NAME" | tr -d '"')
  # Same as above comment
  # Determines if ECS container insights are enabled, and if they are not enabled, then they are turned on
  ARE_CONTAINER_INSIGHTS_ENABLED=$(aws ecs describe-clusters --cluster "$ECS_CLUSTER_NAME_NO_QUOTES" --include SETTINGS | jq '.clusters[0].settings[]|select(.name ==  "containerInsights") | .value' | tr -d '"')

  if [[ "$ARE_CONTAINER_INSIGHTS_ENABLED" != "enabled" ]]
  then
    echo "We are turing on container insights for this cluster: $ECS_CLUSTER_NAME"
    aws ecs update-cluster-settings --cluster "$ECS_CLUSTER_NAME_NO_QUOTES" --settings name=containerInsights,value=enabled
  fi

  yq -i ".$FIELD_TO_MODIFY_IN_YAML = $ECS_CLUSTER_NAME" $TOOLTESTER_CONFIG_FILE_NAME
}

CONTEXT_NAME="$WDL_CONTEXT_NAME"
FIELD_TO_MODIFY_IN_YAML="WDL-ECS-CLUSTER"
Determine_compute_environment_names_and_set_variables

CONTEXT_NAME="$CWL_CONTEXT_NAME"
FIELD_TO_MODIFY_IN_YAML="CWL-ECS-CLUSTER"
Determine_compute_environment_names_and_set_variables

echo "Your config file has been correctly set up, you can now run the tooltester run-workflows-through-wes command"
echo "When you are done with your AGC contexts, please destroy them (see the README for more info)"
