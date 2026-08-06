pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        COMPOSE_PROJECT_NAME = 'tms'
        COMPOSE_CMD_FILE = '.compose_cmd'
        WORK_DIR_FILE = '.workdir'
        ENV_FILE = '.env'
        REPO_URL = 'https://github.com/Neueda-Learning/10_106_CodeWarriors_TransactionMonitoringAlertSystem.git'
        REPO_BRANCH = 'main'
        REPO_CREDENTIALS_ID = ''
    }

    stages {
        stage('Checkout Source') {
            steps {
                script {
                    def workDir = "repo-${env.BUILD_NUMBER}-${UUID.randomUUID().toString().substring(0, 8)}"

                    def repoUrl = env.REPO_URL?.trim()
                    def branch = env.REPO_BRANCH?.trim()
                    def credentialsId = env.REPO_CREDENTIALS_ID?.trim()

                    if (!repoUrl) {
                        error('REPO_URL is required.')
                    }

                    if (!branch) {
                        error('REPO_BRANCH is required.')
                    }

                    // Safety net: remove the target folder only if it happens to already exist.
                    sh "rm -rf '${workDir}' 2>/dev/null || true"

                    if (credentialsId) {
                        // For private repos, keep Jenkins-managed credentials support.
                        dir(workDir) {
                            git branch: branch, credentialsId: credentialsId, url: repoUrl
                        }
                    } else {
                        // For public repos, avoid Git plugin pre-clean behavior on stale folders.
                        sh "git clone --branch '${branch}' --single-branch '${repoUrl}' '${workDir}'"
                    }

                    // Persist the computed folder name to a file: env.WORK_DIR mutations here
                    // do NOT reliably survive into later stages, so every downstream stage
                    // reads this file instead of relying on the env var.
                    writeFile file: env.WORK_DIR_FILE, text: workDir

                    echo "Checked out into workspace subfolder: ${workDir}"
                }
            }
        }

        stage('Validate Agent Tooling') {
            steps {
                script {
                    if (!isUnix()) {
                        error('This pipeline requires a Linux Jenkins agent with git, curl, docker, and either docker compose or docker-compose installed.')
                    }

                    sh 'git --version'
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

        stage('Build Backend') {
            steps {
                script {
                    def workDir = readFile(env.WORK_DIR_FILE).trim()
                    dir("${workDir}/Backend/transactions") {
                        sh 'chmod +x mvnw && ./mvnw -B clean package -DskipTests'
                    }
                }
            }
        }

        stage('Validate Frontend') {
            steps {
                script {
                    def workDir = readFile(env.WORK_DIR_FILE).trim()
                    dir("${workDir}/Frontend") {
                        sh '''
set -e
test -f index.html
test -f app.js
test -f styles.css
echo "Frontend static assets validation passed."
'''
                    }
                }
            }
        }

        stage('Prepare Deployment Env') {
            steps {
                script {
                    def workDir = readFile(env.WORK_DIR_FILE).trim()
                    def envContent = """
MYSQL_ROOT_PASSWORD=${env.MYSQL_ROOT_PASSWORD ?: 'n3u3da!'}
MYSQL_DATABASE=${env.MYSQL_DATABASE ?: 'transactions'}
MYSQL_USER=${env.MYSQL_USER ?: 'tms_user'}
MYSQL_PASSWORD=${env.MYSQL_PASSWORD ?: 'tms_password'}
JWT_SECRET=${env.JWT_SECRET ?: 'd83f5e2a7c1b94d6e8f0a2b4c6d8e0f2a4b6c8d0e2f4a6b8c0d2e4f6a8b0c2d4'}
""".trim() + "\n"

                    writeFile file: "${workDir}/${env.ENV_FILE}", text: envContent
                }
            }
        }

        stage('Deploy MySQL') {
            steps {
                script {
                    def workDir = readFile(env.WORK_DIR_FILE).trim()
                    def composeCmd = readFile(env.COMPOSE_CMD_FILE).trim()
                    dir(workDir) {
                        // Start MySQL ONLY first and wait for it to be healthy. The backend's
                        // DataInitializer runs schema-dependent queries the moment it boots, so
                        // tables must exist BEFORE the backend container is started — starting
                        // the whole stack at once caused the backend to crash-loop and fail its
                        // own healthcheck before schema.sql could be applied.
                        sh "${composeCmd} --env-file .env pull mysql || true"
                        sh "${composeCmd} --env-file .env up -d mysql"
                        sh '''
                            for i in $(seq 1 30); do
                                status=$(docker inspect -f "{{.State.Health.Status}}" tms-mysql 2>/dev/null || echo "starting")
                                if [ "$status" = "healthy" ]; then
                                    echo "MySQL is healthy."
                                    break
                                fi
                                echo "Waiting for MySQL to become healthy... ($i/30)"
                                sleep 5
                            done
                        '''
                    }
                }
            }
        }

        stage('Initialize Database Schema') {
            steps {
                script {
                    def workDir = readFile(env.WORK_DIR_FILE).trim()
                    def composeCmd = readFile(env.COMPOSE_CMD_FILE).trim()
                    dir(workDir) {
                        // schema.sql (mysql/init/schema.sql) is bind-mounted into the mysql
                        // container at /docker-entrypoint-initdb.d/schema.sql. Applying it here
                        // (not just relying on first-boot auto-init) keeps the schema up to date
                        // even if the mysql_data volume already existed from a previous deploy.
                        // CREATE TABLE IF NOT EXISTS makes this safe to re-run every build.
                        // This MUST happen before the backend container starts.
                        sh """
set -e
if ${composeCmd} --env-file .env exec -T mysql sh -c 'mysql -u root -p"\\$MYSQL_ROOT_PASSWORD" "\\$MYSQL_DATABASE" < /docker-entrypoint-initdb.d/schema.sql'; then
    echo "Schema initialized with root account."
elif ${composeCmd} --env-file .env exec -T mysql sh -c 'mysql -u"\\$MYSQL_USER" -p"\\$MYSQL_PASSWORD" "\\$MYSQL_DATABASE" < /docker-entrypoint-initdb.d/schema.sql'; then
    echo "Schema initialized with application account."
else
    echo "Schema initialization failed with both root and application credentials."
    ${composeCmd} --env-file .env logs --tail=150 mysql
    exit 1
fi
"""
                    }
                }
            }
        }

        stage('Deploy Application') {
            steps {
                script {
                    def workDir = readFile(env.WORK_DIR_FILE).trim()
                    def composeCmd = readFile(env.COMPOSE_CMD_FILE).trim()
                    dir(workDir) {
                        // MySQL is already up/healthy and schema is applied; now bring up
                        // (and build) backend + frontend on top of it.
                        sh "${composeCmd} --env-file .env pull || true"
                        sh "${composeCmd} --env-file .env up -d --build --remove-orphans"
                        sh "${composeCmd} --env-file .env ps"
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def workDir = readFile(env.WORK_DIR_FILE).trim()
                    def composeCmd = readFile(env.COMPOSE_CMD_FILE).trim()
                    dir(workDir) {
                        sh "${composeCmd} --env-file .env ps"
                    }
                    sh 'curl -fsS http://localhost:8080/rules >/dev/null'
                    sh 'curl -fsS http://localhost:8085/index.html >/dev/null'
                }
            }
        }
    }

    post {
        success {
            echo 'Deployment pipeline completed successfully.'
        }
        failure {
            echo 'Deployment pipeline failed. Check stage logs above.'
        }
        cleanup {
            script {
                if (fileExists(env.WORK_DIR_FILE)) {
                    def workDir = readFile(env.WORK_DIR_FILE).trim()
                    sh "rm -f '${workDir}/${env.ENV_FILE}'"
                }
                sh 'rm -f .compose_cmd .workdir'
            }
        }
    }
}