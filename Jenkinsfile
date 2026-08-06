pipeline {

    agent any

    environment {
        GIT_URL = 'https://github.com/Neueda-Learning/11-102-NULL-POINTERS-PORTFOLIO-MANAGEMENT.git'
        BRANCH  = 'main'
        APP_PORT = '8090'
        SERVER_PORT = '8085'
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

        stage('Prepare Deploy Env') {
            steps {
                script {
                    if (!(env.FINNHUB_API_KEY ?: '').trim()) {
                        error('FINNHUB_API_KEY is required. Configure it in Jenkins credentials/environment.')
                    }
                }
                writeFile file: '.env', text: """
MYSQL_ROOT_PASSWORD=${env.MYSQL_ROOT_PASSWORD ?: 'root123'}
MYSQL_DATABASE=${env.MYSQL_DATABASE ?: 'portfolio'}
MYSQL_USER=${env.MYSQL_USER ?: 'portfolio_user'}
MYSQL_PASSWORD=${env.MYSQL_PASSWORD ?: 'portfolio_password'}
MYSQL_PORT=${env.MYSQL_PORT ?: '3306'}
APP_PORT=${env.APP_PORT ?: '8090'}
SERVER_PORT=${env.SERVER_PORT ?: '8085'}
FINNHUB_API_KEY=${env.FINNHUB_API_KEY}
FINNHUB_BASE_URL=${env.FINNHUB_BASE_URL ?: 'https://finnhub.io/api/v1'}
FINNHUB_WEBSOCKET_URL=${env.FINNHUB_WEBSOCKET_URL ?: 'wss://ws.finnhub.io'}
""".trim() + "\n"
            }
        }

        stage('Deploy (app + MySQL)') {
            steps {
                sh 'docker-compose --env-file .env up -d'
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
                sh 'docker-compose --env-file .env ps'
                sh 'docker ps --format "table {{.Names}}\\t{{.Status}}"'
            }
        }

        stage('Smoke Check') {
            steps {
                sh '''
                    APP_PORT=${APP_PORT:-8090}
                    for i in $(seq 1 30); do
                        code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${APP_PORT}/" || true)
                        if [ "$code" != "000" ] && [ "$code" -lt 500 ]; then
                            echo "App is reachable (HTTP $code)."
                            exit 0
                        fi
                        echo "Waiting for app to become ready... ($i/30), last code=$code"
                        sleep 5
                    done
                    echo "App did not become ready in time."
                    docker-compose --env-file .env logs --tail=200 app
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            sh 'docker-compose --env-file .env ps || true'
            sh 'rm -f .env || true'
        }
        failure {
            sh 'docker-compose --env-file .env logs --tail=200'
        }
        success {
            echo 'Portfolio deployment pipeline completed successfully.'
        }
    }
}

