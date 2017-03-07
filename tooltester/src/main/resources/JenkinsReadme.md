<<<<<<< HEAD
<<<<<<< HEAD
=======
Last tested with ansible 2.2.1.0
>>>>>>> 8353215... Pretty print reports and added documentation
# Master Setup:
=======
Master Setup:
>>>>>>> d7e8c79... Feature/jenkins example (#5)
1.  Install the Jenkins pipeline suite
<<<<<<< HEAD
2.  Create a pipeline called PipelineTest
3.  Copy the contents of resources/PipelineTest.groovy into the Pipeline Script textbox
4.  Check the checkbox:  "This project is parameterized"
5.  Create 5 String Parameters with the names: "URL", "Tag", "DescriptorPath", "ParameterPath", "DockerfilePath"
<<<<<<< HEAD
6.  Install ansible
=======
2.  Install all plugins filtered with "blue ocean"
3.  Create a pipeline called PipelineTest
4.  Copy the contents of resources/PipelineTest.groovy into the Pipeline Script textbox
5.  Check the checkbox:  "This project is parameterized"
6.  Create 5 String Parameters with the names: "URL", "Tag", "DescriptorPath", "ParameterPath", "DockerfilePath"
7.  Install ansible
>>>>>>> 8353215... Pretty print reports and added documentation
    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
8. Add to /etc/ansible/hosts with the following:
    [local]
    localhost
9.  Get the jenkinsPlaybook.yml from src/main/java/io.dockstore.tooltester/resources
10.  Add jenkins to the docker group
    sudo usermod -aG docker jenkins
11.  Execute the playbook
    ansible-playbook jenkinsPlaybook.yml
12.  Log in as jenkins
    sudo -u jenkins -i
13. Create an ssh keypair for jenkins
    ssh-keygen -t rsa
14. .ssh/id_rsa.pub is your public key
15. Check and see if dockstore command docker command is working correctly.


# Slave Setup:
1. Put the master's public key into the authorized_keys file of jenkins
2. Install ansible
    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:ansible/ansible
    sudo apt-get update
    sudo apt-get install -y ansible
<<<<<<< HEAD
6. Add to /etc/ansible/hosts with the following:
    [local]
    localhost
7. Install python-pip
    sudo apt-get install python-pip
6. Download jenkinsPlaybook.yml
7. Execute the playbook
    ansible-playbook jenkinsPlaybook.yml
=======
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
>>>>>>> d7e8c79... Feature/jenkins example (#5)
=======
3. Add to /etc/ansible/hosts with the following:
    [local]
    localhost
4. Download jenkinsPlaybook.yml
5. Execute the playbook
    ansible-playbook jenkinsPlaybook.yml
6. Add the jenkins user
    sudo useradd -m jenkins
7. Make a .ssh directory
    sudo -u jenkins mkdir /home/jenkins/.ssh
8. Create and edit the authorized_keys file
    sudo -u jenkins vim /home/jenkins/.ssh/authorized_keys
9. Check and see if dockstore command and docker command is working correctly.
>>>>>>> 8353215... Pretty print reports and added documentation
