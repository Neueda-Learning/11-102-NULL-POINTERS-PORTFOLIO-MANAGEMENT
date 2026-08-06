pipeline {

    agent any

    environment {
        GIT_URL = 'https://github.com/<your-org>/Portfolio_project.git'
        BRANCH  = 'main'
    }

    stages {

        stage('Checkout Source') {
            steps {
                git branch: "${BRANCH}", url: "${GIT_URL}"
            }
        }

        stage('Build App (Maven)') {
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw -B clean verify -DskipTests'
            }
        }

        stage('Stop Existing Containers') {
            steps {
                sh 'docker-compose down || true'
            }
        }

        stage('Build Docker Images') {
            steps {
                sh 'docker-compose build --no-cache'
            }
        }

        stage('Deploy (app + MySQL)') {
            steps {
                sh 'docker-compose up -d'
            }
        }

        stage('Wait for MySQL Health') {
            steps {
                sh '''
                    for i in $(seq 1 30); do
                        status=$(docker inspect -f "{{.State.Health.Status}}" portfolio-mysql 2>/dev/null || echo "unknown")
                        echo "MySQL health status: $status ($i/30)"
                        if [ "$status" = "healthy" ]; then
                            echo "MySQL is healthy."
                            exit 0
                        fi
                        sleep 5
                    done
                    echo "MySQL did not become healthy in time."
                    docker-compose logs --tail=200 mysql
                    exit 1
                '''
            }
        }

        stage('Verify Containers Running') {
            steps {
                sh 'docker-compose ps'
                sh 'docker ps --format "table {{.Names}}\\t{{.Status}}"'
            }
        }

        stage('Smoke Check') {
            steps {
                sh '''
                    APP_PORT=${APP_PORT:-8080}
                    for i in $(seq 1 30); do
                        if curl -fsS "http://localhost:${APP_PORT}/" >/dev/null 2>&1; then
                            echo "App is up and responding."
                            exit 0
                        fi
                        echo "Waiting for app to become ready... ($i/30)"
                        sleep 5
                    done
                    echo "App did not become ready in time."
                    docker-compose logs --tail=200 app
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            sh 'docker-compose ps'
        }
        failure {
            sh 'docker-compose logs --tail=200'
        }
        success {
            echo 'Portfolio deployment pipeline completed successfully.'
        }
    }
}

