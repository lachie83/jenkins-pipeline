#!/usr/bin/groovy
package io.estrado;

def kubectlTest() {
    // Test that kubectl can correctly communication with the Kubernetes API
    println "checking kubectl connnectivity to the API"
    sh "kubectl get nodes"

}

def helmLint(String chart_dir) {
    // lint helm chart
    println "running helm lint ${chart_dir}"
    sh "helm lint ${chart_dir}"

}

def helmConfig() {
    //setup helm connectivity to Kubernetes API and Tiller
    println "initiliazing helm client"
    sh "helm init"
    println "checking client/server version"
    sh "helm version"
}


def helmDeploy(Map args) {
    //configure helm client and confirm tiller process is installed
    helmConfig()

    def String namespace

    // If namespace isn't parsed into the function set the namespace to the name
    if (args.namespace == null) {
        namespace = args.name
    } else {
        namespace = args.namespace
    }

    if (args.dry_run) {
        println "Running dry-run deployment"

        sh "helm upgrade --dry-run --install ${args.name} ${args.chart_dir} --set imageTag=${args.version_tag},replicas=${args.replicas},cpu=${args.cpu},memory=${args.memory},ingress.hostname=${args.hostname} --namespace=${namespace}"
    } else {
        println "Running deployment"

        // reimplement --wait once it works reliable
        sh "helm upgrade --install ${args.name} ${args.chart_dir} --set imageTag=${args.version_tag},replicas=${args.replicas},cpu=${args.cpu},memory=${args.memory},ingress.hostname=${args.hostname} --namespace=${namespace}"

        // sleeping until --wait works reliably
        sleep(20)

        echo "Application ${args.name} successfully deployed. Use helm status ${args.name} to check"
    }
}

def helmDelete(Map args) {
        println "Running helm delete ${args.name}"

        sh "helm delete ${args.name}"
}

def helmTest(Map args) {
    println "Running Helm test"

    sh "helm test ${args.name} --cleanup"
}

def gitEnvVars() {
    // create git envvars
    println "Setting envvars to tag container"

    sh 'git rev-parse HEAD > git_commit_id.txt'
    try {
        env.GIT_COMMIT_ID = readFile('git_commit_id.txt').trim()
        env.GIT_SHA = env.GIT_COMMIT_ID.substring(0, 7)
    } catch (e) {
        error "${e}"
    }
    println "env.GIT_COMMIT_ID ==> ${env.GIT_COMMIT_ID}"

    sh 'git config --get remote.origin.url> git_remote_origin_url.txt'
    try {
        env.GIT_REMOTE_URL = readFile('git_remote_origin_url.txt').trim()
    } catch (e) {
        error "${e}"
    }
    println "env.GIT_REMOTE_URL ==> ${env.GIT_REMOTE_URL}"
}


def containerBuildPub(Map args) {

    println "Running Docker build/publish: ${args.host}/${args.acct}/${args.repo}:${args.tags}"

    docker.withRegistry("https://${args.host}", "${args.auth_id}") {

        // def img = docker.build("${args.acct}/${args.repo}", args.dockerfile)
        def img = docker.image("${args.acct}/${args.repo}")
        sh "docker build --build-arg VCS_REF=${env.GIT_SHA} --build-arg BUILD_DATE=`date -u +'%Y-%m-%dT%H:%M:%SZ'` -t ${args.acct}/${args.repo} ${args.dockerfile}"
        for (int i = 0; i < args.tags.size(); i++) {
            img.push(args.tags.get(i))
        }

        return img.id
    }
}

def getContainerTags(config, Map tags = [:]) {

    println "getting list of tags for container"
    def String commit_tag
    def String version_tag

    try {
        // if PR branch tag with only branch name
        if (env.BRANCH_NAME.contains('PR')) {
            commit_tag = env.BRANCH_NAME
            tags << ['commit': commit_tag]
            return tags
        }
    } catch (Exception e) {
        println "WARNING: commit unavailable from env. ${e}"
    }

    // commit tag
    try {
        // if branch available, use as prefix, otherwise only commit hash
        if (env.BRANCH_NAME) {
            commit_tag = env.BRANCH_NAME + '-' + env.GIT_COMMIT_ID.substring(0, 7)
        } else {
            commit_tag = env.GIT_COMMIT_ID.substring(0, 7)
        }
        tags << ['commit': commit_tag]
    } catch (Exception e) {
        println "WARNING: commit unavailable from env. ${e}"
    }

    // master tag
    try {
        if (env.BRANCH_NAME == 'master') {
            tags << ['master': 'latest']
        }
    } catch (Exception e) {
        println "WARNING: branch unavailable from env. ${e}"
    }

    // build tag only if none of the above are available
    if (!tags) {
        try {
            tags << ['build': env.BUILD_TAG]
        } catch (Exception e) {
            println "WARNING: build tag unavailable from config.project. ${e}"
        }
    }

    return tags
}

def getContainerRepoAcct(config) {

    println "setting container registry creds according to Jenkinsfile.json"
    def String acct

    if (env.BRANCH_NAME == 'master') {
        acct = config.container_repo.master_acct
    } else {
        acct = config.container_repo.alt_acct
    }

    return acct
}

@NonCPS
def getMapValues(Map map=[:]) {
    // jenkins and workflow restriction force this function instead of map.values(): https://issues.jenkins-ci.org/browse/JENKINS-27421
    def entries = []
    def map_values = []

    entries.addAll(map.entrySet())

    for (int i=0; i < entries.size(); i++){
        String value =  entries.get(i).value
        map_values.add(value)
    }

    return map_values
}