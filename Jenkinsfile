pipeline {

    agent {
        // Use the kubernetes agent
        kubernetes { 
            label 'java-17-gradle-8'
        }
    }

    stages {
        stage('Build') {
            steps {
                container('java-17-gradle-8') {

                    script {

                        if (env.GIT_BRANCH != "main" && env.GIT_BRANCH != "master") {
                            // When not on master only test to build the installer
                            sh 'gradle --no-build-cache build'
                        } else {
                            withCredentials([
                                file(credentialsId: 'MAVEN_PUBLISH_SONATYPE_GRADLE_PROPERTIES', variable: 'SONATYPE_CREDENTIALS')
                            ]) {
                                // Build and publish
                                sh 'cp \${SONATYPE_CREDENTIALS} ./gradle.properties'
                                sh 'gradle --no-build-cache build publishToMavenLocal publishToSonatype closeAndReleaseSonatypeStagingRepository --warning-mode all'
                                sh 'rm ./gradle.properties'
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Build successfull"
        }

        // Clean after build
        cleanup {
            cleanWs()
        }

        failure {
            emailext body: "${currentBuild.currentResult}: Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}\n More info at: ${env.BUILD_URL}",
                recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']],
                subject: "Jenkins Build ${currentBuild.currentResult}: Job ${env.JOB_NAME}"
        }
    }
}