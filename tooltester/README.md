Last tested with ansible 2.2.1.0

The general idea is to only have the `jenkins` user talk to each other, never using the `ubuntu` user

# Master Setup:
1. Create a c2.micro flavor Ubuntu 18.04 image on Collaboratory
1. Install ansible
```
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
```
1. Download the Ansible playbook
`wget https://raw.githubusercontent.com/ga4gh/dockstore-support/feature/playbook/tooltester/src/main/resources/jenkinsMasterPlaybook.yml`
1.  Execute the playbook
    `ansible-playbook jenkinsPlaybook.yml`
1. Log out and log back in
1. Run the jenkins container
    `docker run -u root --rm -d -p 8080:8080 -p 50000:50000 -v jenkins-data:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock jenkinsci/blueocean`
1. Follow the setup wizard and install the ansible plugin (may need to restart the docker container), then set shell executable to /bin/bash
1. Create a pipeline called PipelineTest
1. Copy the contents of resources/PipelineTest.groovy into the Pipeline Script textbox
1. Check the checkbox:  "This project is parameterized"
1. Create the String Parameters mentioned in the [constructParameterMap](https://github.com/ga4gh/dockstore-support/blob/develop/tooltester/src/main/java/io/dockstore/tooltester/client/cli/Client.java#L609) function. These string parameters are used to configure the Jenkins pipelines to run the correct tool/workflow, versions, etc.
1. Change master node to not be used (0 executors)
1. Log in as jenkins `sudo -u jenkins -i` and create a .ssh/id_rsa (use the same one as before, ask around for it)


# Slave Setup:
1. Create a c2.large flavor Ubuntu 18.04 image on Collaboratory and give it the JenkinsMaster2 key pair
1. Install ansible
```
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt update -yq
    sudo apt install ansible -yq
```
1. Download the Ansible playbook
    `wget https://raw.githubusercontent.com/ga4gh/dockstore-support/feature/playbook/tooltester/src/main/resources/jenkinsSlavePlaybook.yml`
1. Execute the playbook
        `ansible-playbook jenkinsSlavePlaybook.yml`
1. Remove password prompt by using `sudo visudo` and append `jenkins ALL=(ALL) NOPASSWD: ALL`
1. Configure the aws cli using `sudo -u jenkins -i` and then `aws configure`. Use the credentials in the jenkins@jenkins-master's ~/.aws/credentials
1. Configure slave on master Jenkin: Manage Jenkins => Manage Nodes => New Node => Permanent Agent => Remote root directory: /home/jenkins
1. Reduce executors to 1

# Running tooltester:
1. Check .tooltester/config to see if the 'server-url' needs to be changed
1. Check .tooltester/config to see if the runners specified in it are correct.  An example config that runs all runners is:
```
runner = cromwell cwltool cwl-runner
```
1. Check .tooltester/config to see if dockstore-version needs to be changed
1. Modify the [cwltoolPlaybook](src/main/resources/cwltoolPlaybook.yml) and [toilPlaybook](src/main/resources/toilPlaybook.yml) to have the right apt/pip dependencies if needed (i.e. Check the [dockstore website /onboarding](https://dockstore.org/onboarding) or [GitHub](https://github.com/dockstore/dockstore-ui2/blob/develop/src/app/loginComponents/onboarding/downloadcliclient/downloadcliclient.component.ts#L81) Step 2 Part 3 to see if changes are needed).
1. Check that the slave has enough disk space, remove /tmp and ~/workspace/* (workspace `@tmp` folders aren't removed with cleanup plugin) if needed
