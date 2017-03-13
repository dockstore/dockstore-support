def buildJob = [:]
buildJob["Build " + params.DockerfilePath] = transformIntoDockerfileStep()
def ParameterPaths = params.ParameterPath.split(' ')
def DescriptorPaths = params.DescriptorPath.split(' ')
for (int i = 0; i < ParameterPaths.length; i++) {
    buildJob["Test " + ParameterPaths[i]] = transformIntoStep(params.URL, params.Tag, DescriptorPaths[i], ParameterPaths[i])

}
parallel buildJob

def transformIntoStep(url, tag, descriptor, parameter) {
    // We need to wrap what we return in a Groovy closure, or else it's invoked
    // when this method is called, not when we pass it to parallel.
    // To do this, you need to wrap the code below in { }, and either return
    // that explicitly, or use { -> } syntax.
    return {
        node {
            ws {
                step([$class: 'WsCleanup'])
                sh 'git clone ${URL} target'
                dir('target') {
                    sh 'git checkout ${Tag}'
                    FILE = sh(script: "set -o pipefail && dockstore tool launch --entry $descriptor --local-entry --json $parameter | tee /dev/stderr | sed -n -e 's/^.*Saving copy of cwltool stdout to: //p'", returnStdout: true).trim()
                    sh "mv $FILE $parameter"
                    archiveArtifacts artifacts: parameter
                }

            }
        }

    }
}

def transformIntoDockerfileStep() {
    return {
        node {
            ws {
                step([$class: 'WsCleanup'])
                sh 'git clone ${URL} .'
                sh 'git checkout ${Tag}'
                LOCATION = sh(script: 'dirname "${DockerfilePath}"', returnStdout: true).trim()
                dir(LOCATION) {
                    sh 'docker build .'
                }

            }
        }
    }
}
