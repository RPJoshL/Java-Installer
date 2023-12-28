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
                            // Only test to build the installer when we are not on the master branch
                            sh 'gradle --no-build-cache build'
                        } else {

                            // Get the version to release 
                            def version = sh (
						        script: 'git describe --tags --abbrev=0',
						        returnStdout: true
					        ).replace("\n", "")
                            if (version == null || version.allWhitespace) {
                                error("Commit is not tagged with a version")
                            }
                            def versionV = version.replaceFirst("v", "")

                            // Write the version into the version file
                            sh "echo ${versionV} > VERSION"
                            echo "Building and publishing version ${versionV}"

                            // Build and publish
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