currentBuild.displayName = "1.4.0-beta.1"
def buildJob = [:]
if ("tool".equalsIgnoreCase(params.EntryType)) {
    buildJob["Build " + params.DockerfilePath] = transformIntoDockerfileStep()
}
def ParameterPaths = params.ParameterPath.split(' ')
def DescriptorPaths = params.DescriptorPath.split(' ')
for (int i = 0; i < ParameterPaths.length; i++) {
    buildJob["Test " + ParameterPaths[i]] = transformIntoStep(params.URL, params.Tag, DescriptorPaths[i], ParameterPaths[i], params.EntryType, params.SynapseCache)
}
parallel buildJob

def transformIntoStep(url, tag, descriptor, parameter, entryType, synapseCache) {
    // We need to wrap what we return in a Groovy closure, or else it's invoked
    // when this method is called, not when we pass it to parallel.
    // To do this, you need to wrap the code below in { }, and either return
    // that explicitly, or use { -> } syntax.
    return {

        node {
            ws {
                sh 'rm -rf /mnt/output/*'
                sh 'rm -rf /media/large_volume/output/*'
                step([$class: 'WsCleanup'])
                sh "wget https://raw.githubusercontent.com/ga4gh/dockstore-support/feature/setRunners/tooltester/src/main/resources/${AnsiblePlaybook}.yml"
                ansiblePlaybook playbook: '${AnsiblePlaybook}.yml', sudo: true, sudoUser: null
                sh 'dockstore --version --script || true'
                sh 'pip list'
                sh 'dockstore plugin list --script || true'
                sh 'git clone ${URL} target'
                sh 'echo -e ${Config} > ~/.dockstore/config'
                dir('target') {
                    sh 'git checkout ${Tag}'
                    if (synapseCache != "") {
                        sh 'aws s3 --endpoint-url https://object.cancercollaboratory.org:9080 cp --recursive s3://dockstore/test_files/${SynapseCache}/ .'
                        // sh 's3cmd get --skip-existing --recursive s3://dockstore/test_files/${SynapseCache}/'
                    }
                    // Currently determining whether a file is yaml or json based on its file extension
                    if (parameter.contains('.yml') || paramater.contains('.yaml')) {
                        sh 'echo dockstore $(entryType) launch --local-entry $(descriptor) --yaml $(parameter) --script'
                        FILE = sh (script: "set -o pipefail && dockstore $entryType launch --local-entry $descriptor --yaml $parameter --script | tee /dev/stderr | sed -n -e 's/^.*Saving copy of cwltool stdout to: //p'", returnStdout: true).trim()
                    } else {
                        sh 'echo dockstore $(entryType) launch --local-entry $(descriptor) --json $(parameter) --script'
                        FILE = sh (script: "set -o pipefail && dockstore $entryType launch --local-entry $descriptor --yaml $parameter --script | tee /dev/stderr | sed -n -e 's/^.*Saving copy of cwltool stdout to: //p'", returnStdout: true).trim()
                    }
                    if (JOB_NAME.contains("cwltool") || JOB_NAME.contains("cwl-runner")) {
                        sh "mv $FILE $parameter"
                        archiveArtifacts artifacts: parameter
                    }
                }
            }
        }

    }
}

def transformIntoDockerfileStep(){
    return {
        node {
            ws {
                step([$class: 'WsCleanup'])
                sh 'docker version'
                sh 'git clone ${URL} .'
                sh 'git checkout ${Tag}'
                LOCATION = sh (script: 'dirname "${DockerfilePath}"', returnStdout: true).trim()
                dir(LOCATION){
                    sh 'docker build --no-cache .'
                }
            }
        }
    }
}
