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

        stage('Wait for Health') {
            steps {
                sh 'sleep 30'
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
                sh 'curl -fsS http://localhost:${APP_PORT:-8080}/ >/dev/null'
            }
        }
    }

    post {
        always {
            sh 'docker-compose ps'
        }
        failure {
            sh 'docker-compose logs --tail=100'
        }
        success {
            echo 'Portfolio deployment pipeline completed successfully.'
        }
    }
}

