Master Setup:
1.  Install the Jenkins pipeline suite
2.  Create a pipeline called PipelineTest
3.  Copy the contents of resources/PipelineTest.groovy into the Pipeline Script textbox
4.  Check the checkbox:  "This project is parameterized"
5.  Create 5 String Parameters with the names: "URL", "Tag", "DescriptorPath", "ParameterPath", "DockerfilePath"
6.  sudo apt-get install ansible
7.  Get the jenkinsPlaybook.yml from src/main/java/io.dockstore.tooltester/resources
7.  ansible-playbook jenkinsPlaybook.yml

Slave Setup:
1. sudo apt-get update
2. sudo apt-get upgrade
3. sudo apt-get install default-jre
4. sudo useradd -m jenkins
5. sudo -u jenkins mkdir /home/jenkins/.ssh
6. sudo -u jenkins vim /home/jenkins/.ssh/authorized_keys
7. Put the master's key into the authorized_keys file
8. sudo visudo and then add jenkins ALL= NOPASSWD: ALL
9. Downloading jenkinsPlaybook.yml
10. ansible-playbook jenkinsPlaybook.yml