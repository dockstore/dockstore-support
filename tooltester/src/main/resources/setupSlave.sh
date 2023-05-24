#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

sudo apt update
sudo apt install -yq software-properties-common
sudo apt-add-repository --yes --update ppa:ansible/ansible
sudo apt install ansible -yq
wget https://raw.githubusercontent.com/ga4gh/dockstore-support/feature/playbook/tooltester/src/main/resources/jenkinsSlavePlaybook.yml
ansible-playbook jenkinsSlavePlaybook.yml
echo 'jenkins ALL=(ALL) NOPASSWD: ALL' | sudo EDITOR='tee -a' visudo
