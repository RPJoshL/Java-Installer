pipeline {
    agent any

    tools { 
        jdk '17' 
    }

    stages {
        stage('Build') {
            steps {
                sh './gradlew build publishToMavenLocal --warning-mode all'
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