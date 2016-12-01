node {
    def ocCmd = 'oc'
    def mvnCmd = 'mvn'
    
	def app = 'demo'
    def project = 'project'
    def v = version()

    stage "Build"
    git branch: 'master', url: 'https://github.com/heywoodbanks/bootwildfly.git'
    bat "${mvnCmd} clean install"
    
    stage "Prepare deploy"
    bat "rm -rf oc-build"
    bat "mkdir oc-build\\deployments"
    bat "cp target\\ROOT.war oc-build\\deployments\\ROOT.war"
    bat "${ocCmd} logout"
    bat "${ocCmd} login localhost:8443 -u admin -p admin --insecure-skip-tls-verify=true"
    
    stage "Deploy to Dev"
    //clean up old, keep imagestream
    bat "${ocCmd} delete bc,dc,svc,route -l app=${app} -n ${project}-dev"
    // create build
    bat "${ocCmd} new-build --name=${app} --image-stream=wildfly --binary=true --labels=app=${app} -n ${project}-dev || true"
    //S2I build using binary as input (skips maven build)
    bat "${ocCmd} start-build ${app} --from-dir=oc-build --wait=true -n ${project}-dev"
    // deploy image
    bat "${ocCmd} new-app ${app}:latest -n ${project}-dev"
    // and the route
    bat "${ocCmd} expose svc/${app} -n ${project}-dev"
    
    stage "Integration Test"
    timeout(time: 5, unit: 'MINUTES') {
        retry(500) {
            bat "${mvnCmd} verify -P integration-test -DrouteUrl=http://${app}-${project}-dev.apps.10.2.2.2.xip.io"
        }
    }
    
    stage "Promote to Test"
    //tag for test
    bat "${ocCmd} tag ${project}-dev/${app}:latest ${project}-test/${app}:${v}"
    //clean up test, keep the imagesteam
    bat "${ocCmd} delete bc,dc,svc,route -l app=${app} -n ${project}-test"
    // deploy test image
    bat "${ocCmd} new-app ${app}:${v} -n ${project}-test"
    //and the route
    bat "${ocCmd} expose svc/${app} -n ${project}-test"
    
}

def version() {
  def matcher = readFile('pom.xml') =~ '<version>(.+)</version>'
  matcher ? matcher[0][1] : null
}