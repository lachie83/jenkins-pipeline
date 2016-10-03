def kubectl_proxy() {
    // use kubectl proxy to connect with Kubernetes API
    echo "setting up kubectl"

    sh "kubectl proxy &"
    sh "kubectl --server=http://localhost:8001 get nodes"

}

def helm_deploy(Map args) {
    //configure helm client and confirm tiller process is installed
    sh "/usr/local/linux-amd64/helm init"

    // determine if application is released if not then install. This will be replaced with upgrade --install
    sh "/usr/local/linux-amd64/helm status ${config.app.name} || /usr/local/linux-amd64/helm install ${pwd}/charts/croc-hunter --name ${config.app.name} --set ImageTag=${env.BUILD_NUMBER},Replicas=${config.app}.replicas,Cpu=${config.app.cpu},Memory=${config.app.memory} --namespace=${config.app.name}"
    // If released then upgrade.
    sh "/usr/local/linux-amd64/helm upgrade ${config.app.name} ${pwd}/charts/croc-hunter --set ImageTag=${env.BUILD_NUMBER},Replicas=${config.app.replicas},Cpu=${config.app.cpu},Memory=${config.app.memory}"

    echo "Application ${config.app.name} successfully deployed. Use helm status ${config.app.name} to confirm status"
}
