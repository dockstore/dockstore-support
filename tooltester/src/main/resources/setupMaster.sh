#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

sudo apt-add-repository ppa:ansible/ansible -y
sudo apt update -yq
sudo apt install ansible -yq
wget https://raw.githubusercontent.com/ga4gh/dockstore-support/feature/playbook/tooltester/src/main/resources/jenkinsMasterPlaybook.yml
ansible-playbook jenkinsMasterPlaybook.yml
