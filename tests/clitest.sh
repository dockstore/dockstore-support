#!/bin/bash

# Smoke test for the CLI
# Make sure your ~/.dockstore/config is pointing to the right server! E.g., staging or prod, as appropriate
# Python 3 should be available. I suggest running this in a virtual environment.
set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

echo "Testing a no params workflow"
echo "The output should be a simple String of 'Hello from somewhere'"
echo {} > empty.json
dockstore workflow launch --entry github.com/dockstore-testing/wes-testing/single-descriptor-no-input:main --json empty.json


echo "Testing convert"
dockstore workflow convert entry2json --entry github.com/dockstore-testing/wes-testing/single-descriptor-with-input:main > Dockstore.json

echo "Testing a params workflow"
echo "The output should be a file. cat the file contents which will contain a String of 'hello String!'"

dockstore workflow launch --entry github.com/dockstore-testing/wes-testing/single-descriptor-with-input:main --json Dockstore.json

echo "Testing a tool, cwltool had best be available"
curl -o requirements.txt "https://staging.dockstore.org/api/metadata/runner_dependencies?client_version=1.12.0&python_version=3"
pip install -r requirements.txt
cwltool --version

rm Dockstore.json

cat <<EOT >> Dockstore.json
{
  "mem_gb": 4,
  "bam_input": {
    "path": "https://github.com/CancerCollaboratory/dockstore-tool-bamstats/raw/develop/rna.SRR948778.bam",
    "format": "http://edamontology.org/format_2572",
    "class": "File"
  },
  "bamstats_report": {
    "path": "/tmp/bamstats_report.zip",
    "class": "File"
  }
}
EOT

dockstore tool launch --entry quay.io/collaboratory/dockstore-tool-bamstats:1.25-6_1.0 --json Dockstore.json

