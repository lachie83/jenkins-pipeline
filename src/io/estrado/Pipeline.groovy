#!/usr/bin/groovy
package io.estrado;


def kubectlProxy() {
    // use kubectl proxy to connect with Kubernetes API
    echo "setting up kubectl"

    sh "kubectl proxy &"
    sh "sleep 5"
    sh "kubectl --server=http://localhost:8001 get nodes"

}

def helmDeploy(Map args) {
    //configure helm client and confirm tiller process is installed
    sh "/usr/local/linux-amd64/helm init"

    sh "/usr/local/linux-amd64/helm upgrade --install ${args.name} ${pwd}/charts/croc-hunter --set ImageTag=${args.build_number},Replicas=${args.replicas},Cpu=${args.cpu},Memory=${args.memory} --namespace=${args.name}"

    echo "Application ${args.name} successfully deployed. Use helm status ${args.name} to check"
}
