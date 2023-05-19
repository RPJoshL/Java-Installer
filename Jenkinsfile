pipeline {
    agent any

    tools { 
        jdk '17' 
    }

    stages {
        stage('Build') {
            steps {
                script {
                    withCredentials([
                        file(credentialsId: 'MAVEN_PUBLISH_SONATYPE_GRADLE_PROPERTIES', variable: 'SONATYPE_CREDENTIALS')
                    ]) {
                        // Build and publish
                        sh 'cp \${SONATYPE_CREDENTIALS} ./gradle.properties'
                        sh './gradlew --no-build-cache build publishToMavenLocal publishToSonatype closeAndReleaseSonatypeStagingRepository --warning-mode all'
                        sh 'rm ./gradle.properties'
                    }
                    
                }
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'release/installer-*', fingerprint: true
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