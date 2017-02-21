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
1. sudo useradd -m jenkins
2. sudo -u jenkins mkdir /home/jenkins/.ssh
3. sudo -u jenkins vim /home/jenkins/.ssh/authorized_keys
4. Put the master's key into the authorized_keys file
5. sudo visudo and then add jenkins ALL= NOPASSWD: ALL
6. Download jenkinsPlaybook.yml
7. ansible-playbook jenkinsPlaybook.yml