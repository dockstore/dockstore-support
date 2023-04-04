#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset

# This script takes in the validation file that DNAstack provided containing workflow names that validated successfully with miniwdl
# and transforms it to a CSV file that can be used with the metrics aggregator's submit-metrics-data command.
# This script can likely be removed after discussing with DNAstack the format that we want the validation data in.

DATA_FILE_PATH="${1}"
DELIMITER=','
IS_VALID=true # DNAstack provided a file with workflow names that validated successfully with miniwdl

DATE=$(date +%Y-%m-%dT%H-%M-%S%z)
FORMATTED_DATA_FILE=formattedData_"${DATE}".txt
echo "trsId,versionName,isValid,dateExecuted" > "${FORMATTED_DATA_FILE}"

while read -r LINE
do
  # Split the workflow name to extract the TRS ID and version name
  IFS=':' read -r -a WORKFLOW_NAME_COMPONENTS <<< "$LINE"
  if [[ "${#WORKFLOW_NAME_COMPONENTS[@]}" -eq 2 ]]; then
    FORMATTED_DATA_LINE="${WORKFLOW_NAME_COMPONENTS[0]}"
    FORMATTED_DATA_LINE+="${DELIMITER}"
    FORMATTED_DATA_LINE+="${WORKFLOW_NAME_COMPONENTS[1]}"
    FORMATTED_DATA_LINE+="${DELIMITER}"
    FORMATTED_DATA_LINE+="${IS_VALID}"
    FORMATTED_DATA_LINE+="${DELIMITER}"
    # DNAstack didn't provide a date. Use current date, specified in ISO 8601 UTC date format
    DATE_EXECUTED=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    FORMATTED_DATA_LINE+="${DATE_EXECUTED}"
    echo "${FORMATTED_DATA_LINE}" >> "${FORMATTED_DATA_FILE}"
  fi
done < "${DATA_FILE_PATH}"