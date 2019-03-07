Last tested with ansible 2.2.1.0
# Master Setup:
1.  Install the Jenkins pipeline suite
2.  Install these plugins:
- Blue Ocean 1.0.1
- build timeout plugin 1.18
- build-name-setter 1.6.5
- Pipeline 2.5
- SSH Slaves plugin 1.13
- Timestamper 1.8.8
- Workspace Cleanup Plugin 0.32
3.  Create a pipeline called PipelineTest
4.  Copy the contents of resources/PipelineTest.groovy into the Pipeline Script textbox
5.  Check the checkbox:  "This project is parameterized"
6.  Create the String Parameters mentioned in the [constructParameterMap](https://github.com/ga4gh/dockstore-support/blob/develop/tooltester/src/main/java/io/dockstore/tooltester/client/cli/Client.java#L609) function. These string parameters are used to configure the Jenkins pipelines to run the correct tool/workflow, versions, etc.
7.  Install ansible
```
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
```
9.  Get the jenkinsPlaybook.yml from src/main/java/io.dockstore.tooltester/resources
10.  Add jenkins to the docker group
    `sudo usermod -aG docker jenkins`
11.  Execute the playbook
    `ansible-playbook jenkinsPlaybook.yml`
12.  Log in as jenkins
    `sudo -u jenkins -i`
13. Create an ssh keypair for jenkins
    `ssh-keygen -t rsa`
14. .ssh/id_rsa.pub is your public key
15. Check and see if `dockstore` command docker command is working correctly.


# Slave Setup:
1. Create a c2.large flavor Ubuntu 18.04 image on Collaboratory and give it the JenkinsMaster key pair
2. Install ansible
```
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt update -yq
    sudo apt install ansible -yq
```
3. Download an ansible playbook
    `wget https://raw.githubusercontent.com/ga4gh/dockstore-support/feature/playbook/tooltester/src/main/resources/jenkinsSlavePlaybook.yml`
4. Execute the playbook
        `ansible-playbook jenkinsSlavePlaybook.yml`
5. Remove password prompt by using `sudo visudo` and append `jenkins ALL=(ALL) NOPASSWD: ALL`
6. Configure the aws cli using `sudo -u jenkins -i` and then `aws configure`. Use the credentials in the jenkins@jenkins-master's ~/.aws/credentials
7. Configure slave on master Jenkin: Manage Jenkins => Manage Nodes => New Node => Permanent Agent => Remote root directory: /home/jenkins

# Running tooltester:
1. Check .tooltester/config to see if the 'server-url' needs to be changed
2. Check .tooltester/config to see if the runners specified in it are correct.  An example config that runs all runners is:
```
runner = cromwell cwltool cwl-runner
```
3. Check .tooltester/config to see if dockstore-version needs to be changed
4. Modify the [cwltoolPlaybook](src/main/resources/cwltoolPlaybook.yml) and [toilPlaybook](src/main/resources/toilPlaybook.yml) to have the right apt/pip dependencies if needed (i.e. Check the [dockstore website /onboarding](https://dockstore.org/onboarding) or [GitHub](https://github.com/dockstore/dockstore-ui2/blob/develop/src/app/loginComponents/onboarding/downloadcliclient/downloadcliclient.component.ts#L81) Step 2 Part 3 to see if changes are needed).
5. Check that the slave has enough disk space
