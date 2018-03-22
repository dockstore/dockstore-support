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
    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
```
8. Add to /etc/ansible/hosts with the following:
```
    [local]
    localhost
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
1. Install ansible
```
    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
```
2. Add to /etc/ansible/hosts with the following:
```
    [local]
    localhost
```
3. Download jenkinsPlaybook.yml
4. Add the jenkins user
    `sudo useradd -m jenkins`
5. Make a .ssh directory
    `sudo -u jenkins mkdir /home/jenkins/.ssh`
6. Create and edit the authorized_keys file
    `sudo -u jenkins vim /home/jenkins/.ssh/authorized_keys`
7. Execute the playbook
    `ansible-playbook jenkinsPlaybook.yml`
8. Give add jenkins to docker group
    `sudo usermod -aG docker jenkins`
9. Put the master's public key into the authorized_keys file of jenkins
10. Check and see if `dockstore` command and docker command is working correctly.
Giving jenkins sudo access
11. `sudo visudo`
12. Append `jenkins ALL=(ALL) NOPASSWD: ALL`
13. Give sudo access to jenkins
    `sudo usermod -a -G sudo jenkins`

# Running tooltester:
1. Check .tooltester to see if the 'server-url' needs to be changed
2. Modify the playbook on ubuntu@JenkinsMaster to have the right dockstore version
3. Modify the apt and pip dependencies if needed (i.e. Check the [dockstore website /onboarding](https://dockstore.org/onboarding) or [GitHub](https://github.com/dockstore/dockstore-ui2/blob/develop/src/app/loginComponents/onboarding/downloadcliclient/downloadcliclient.component.ts#L81) Step 2 Part 3 to see if changes are needed).
4. Run the playbook on ubuntu@JenkinsMaster
5. `sudo -u jenkins -i` and then `dockstore` to confirm the right version
6. Copy the playbook on JenkinsMaster to the ubuntu@JenkinsSlaves
7. Run the playbook on ubuntu@JenkinsSlaves
8. `sudo -u jenkins -i` and then `dockstore` to confirm the right version
9. Configure the PipelineTest's `currentBuild.display` name to the Dockstore version that's going to be ran.
