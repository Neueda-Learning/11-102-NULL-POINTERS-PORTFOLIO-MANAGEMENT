pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        COMPOSE_CMD_FILE = '.compose_cmd'
        ENV_FILE = '.env'
        APP_PORT = '8085'
        SERVER_PORT = '8080'
    }

    stages {
        stage('Checkout Source') {
            steps {
                checkout scm
            }
        }

        stage('Validate Tooling') {
            steps {
                script {
                    if (!isUnix()) {
                        error('This pipeline requires a Linux Jenkins agent with docker, curl, and docker compose/docker-compose.')
                    }

                    sh 'docker --version'
                    sh 'curl --version'

                    def composeCmd = sh(
                            script: '''
if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
elif docker-compose version >/dev/null 2>&1; then
    echo "docker-compose"
fi
''',
                            returnStdout: true
                    ).trim()

                    if (!composeCmd) {
                        error('Neither docker compose nor docker-compose is available on this Jenkins agent.')
                    }

                    writeFile file: env.COMPOSE_CMD_FILE, text: composeCmd + "\n"
                    sh "${composeCmd} version"
                }
            }
        }

        stage('Build App') {
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw -B clean package -DskipTests'
            }
        }

        stage('Prepare Deploy Env') {
            steps {
                script {
                    def finnhubKey = (env.FINNHUB_API_KEY ?: '').trim()
                    if (!finnhubKey) {
                        error('FINNHUB_API_KEY is required. Configure it in Jenkins credentials/environment.')
                    }

                    def envContent = """
MYSQL_ROOT_PASSWORD=${env.MYSQL_ROOT_PASSWORD ?: 'root123'}
MYSQL_DATABASE=${env.MYSQL_DATABASE ?: 'portfolio'}
MYSQL_USER=${env.MYSQL_USER ?: 'portfolio_user'}
MYSQL_PASSWORD=${env.MYSQL_PASSWORD ?: 'portfolio_password'}
MYSQL_PORT=${env.MYSQL_PORT ?: '3306'}
APP_PORT=${env.APP_PORT ?: '8085'}
SERVER_PORT=${env.SERVER_PORT ?: '8080'}
FINNHUB_API_KEY=${finnhubKey}
FINNHUB_BASE_URL=${env.FINNHUB_BASE_URL ?: 'https://finnhub.io/api/v1'}
FINNHUB_WEBSOCKET_URL=${env.FINNHUB_WEBSOCKET_URL ?: 'wss://ws.finnhub.io'}
GEMINI_API_KEY=${(env.GEMINI_API_KEY ?: '').trim()}
""".trim() + "\n"

                    writeFile file: env.ENV_FILE, text: envContent
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    def composeCmd = readFile(env.COMPOSE_CMD_FILE).trim()
                    sh "${composeCmd} --env-file .env down --remove-orphans || true"
                    sh "${composeCmd} --env-file .env up -d --build --remove-orphans"
                    sh "${composeCmd} --env-file .env ps"
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def composeCmd = readFile(env.COMPOSE_CMD_FILE).trim()

                    sh '''
APP_PORT=${APP_PORT:-8085}
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${APP_PORT}/api-docs" || true)
  if [ "$code" = "200" ]; then
    echo "App health endpoint is ready (HTTP $code)."
    exit 0
  fi
  echo "Waiting for app readiness... ($i/30), last code=$code"
  sleep 5
done
echo "App did not become ready in time."
exit 1
'''

                    // Frontend is served by Spring static resources via the same app container.
                    sh 'curl -fsS http://localhost:8085/ >/dev/null'
                    sh "${composeCmd} --env-file .env ps"
                }
            }
        }
    }

    post {
        always {
            script {
                def composeCmd = fileExists(env.COMPOSE_CMD_FILE) ? readFile(env.COMPOSE_CMD_FILE).trim() : ''
                if (composeCmd) {
                    sh "${composeCmd} --env-file .env ps || true"
                    sh "${composeCmd} --env-file .env logs --tail=150 || true"
                }
            }
            sh 'rm -f .env .compose_cmd || true'
        }
        success {
            echo 'Deployment pipeline completed successfully.'
        }
        failure {
            echo 'Deployment pipeline failed. Review container logs above.'
        }
    }
}