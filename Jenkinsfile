pipeline {
    agent any

    environment {
        REGISTRY = "registry-gorani.lab.terminal-lab.kr"
        HARBOR_CREDENTIALS_ID = "harbor-auth"

        GIT_URL = "https://github.com/ShinHanSWT1/EcoDrive_Pay.git"
        GIT_BRANCH = "dev"

        PROJECT_NAME = "gorani"
        IMAGE_NAME = "pay"

        REMOTE_SERVER = "10.0.1.79"
        REMOTE_USER = "rocky"
        REMOTE_POD_NAME = "gorani-dev-pod"
        CONTAINER_NAME = "dev-pay"

        SPRING_PROFILE = "dev"

        DB_URL = "jdbc:postgresql://localhost:5433/pay_dev"
        DB_HOST = "localhost"
        DB_NAME = "pay_dev"
        DB_USERNAME = "postgres"
        DB_PORT = "5432"

        REDIS_HOST = "localhost"
        REDIS_PORT = "6379"

        FRONTEND_URL = "https://dev-gorani.lab.terminal-lab.kr/"

        FULL_IMAGE_TAG = "${REGISTRY}/${PROJECT_NAME}/${IMAGE_NAME}:${BUILD_NUMBER}"
        LATEST_IMAGE_TAG = "${REGISTRY}/${PROJECT_NAME}/${IMAGE_NAME}:latest"

        AWS_BUCKET_NAME = "eecodrive"
    }

    stages {
        stage('1. Checkout') {
            steps {
                git branch: "${GIT_BRANCH}", url: "${GIT_URL}"
            }
        }

        stage('2. Build Image') {
            steps {
                script {
                    sh "podman build -t ${FULL_IMAGE_TAG} ."
                    sh "podman tag ${REGISTRY}/${PROJECT_NAME}/${IMAGE_NAME}:${BUILD_NUMBER} ${REGISTRY}/${PROJECT_NAME}/${IMAGE_NAME}:latest"
                }
            }
        }

        stage('3. Push to Harbor') {
            steps {
                withCredentials([usernamePassword(credentialsId: HARBOR_CREDENTIALS_ID, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh "podman login ${REGISTRY} -u ${USER} -p ${PASS}"
                    sh "podman push ${REGISTRY}/${PROJECT_NAME}/${IMAGE_NAME}:${BUILD_NUMBER} --tls-verify=false"
                    sh "podman push ${REGISTRY}/${PROJECT_NAME}/${IMAGE_NAME}:latest --tls-verify=false"
                }
            }
        }

        stage('4. Cleanup') {
            steps {
                sh "podman rmi ${REGISTRY}/${PROJECT_NAME}/${IMAGE_NAME}:${BUILD_NUMBER}"
                sh "podman rmi ${REGISTRY}/${PROJECT_NAME}/${IMAGE_NAME}:latest"
            }
        }

        stage('5. Deploy Dev') {
            steps {
                sshagent(['dev-server-ssh']) {
                    withCredentials([
                        usernamePassword(
                            credentialsId: HARBOR_CREDENTIALS_ID,
                            usernameVariable: 'USER',
                            passwordVariable: 'PASS'
                        ),
                        string(credentialsId: 'db-password-dev', variable: 'DB_PASSWORD'),
                        string(credentialsId: 'redis-password-dev', variable: 'REDIS_PASSWORD'),
                        string(credentialsId: 'jwt-secret-dev', variable: 'JWT_SECRET'),
                        string(credentialsId: 'kakao-client-id-dev', variable: 'KAKAO_CLIENT_ID'),
                        string(credentialsId: 'kakao-client-secret-dev', variable: 'KAKAO_CLIENT_SECRET'),
                        string(credentialsId: 'aws-s3-access-key', variable: 'AWS_ACCESS_KEY'),
                        string(credentialsId: 'aws-s3-secret-key', variable: 'AWS_SECRET_KEY')
                    ]) {
                        script {
                            sh '''
                                ssh -o StrictHostKeyChecking=no ${REMOTE_USER}@${REMOTE_SERVER} << EOF
                                    sudo podman login ${REGISTRY} -u "${USER}" -p "${PASS}" --tls-verify=false

                                    sudo podman rm -f ${CONTAINER_NAME} || true
                                    sudo podman pull ${FULL_IMAGE_TAG} --tls-verify=false

                                    sudo podman run -d --name ${CONTAINER_NAME} \
                                        --pod ${REMOTE_POD_NAME} \
                                        --restart always \
                                        -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILE} \
                                        -e DB_URL=${DB_URL} \
                                        -e DB_HOST=${DB_HOST} \
                                        -e DB_PORT=${DB_PORT} \
                                        -e DB_NAME=${DB_NAME} \
                                        -e DB_USERNAME=${DB_USERNAME} \
                                        -e DB_PASSWORD="$DB_PASSWORD" \
                                        -e REDIS_HOST=${REDIS_HOST} \
                                        -e REDIS_PORT=${REDIS_PORT} \
                                        -e REDIS_PASSWORD="$REDIS_PASSWORD" \
                                        -e JWT_SECRET="$JWT_SECRET" \
                                        -e KAKAO_CLIENT_ID="$KAKAO_CLIENT_ID" \
                                        -e KAKAO_CLIENT_SECRET="$KAKAO_CLIENT_SECRET" \
                                        -e FRONTEND_URL=${FRONTEND_URL} \
                                        -e AWS_ACCESS_KEY="$AWS_ACCESS_KEY" \
                                        -e AWS_SECRET_KEY="$AWS_SECRET_KEY" \
                                        -e AWS_BUCKET_NAME=${AWS_BUCKET_NAME} \
                                        ${FULL_IMAGE_TAG}

                                    sudo podman image prune -f
EOF
                            '''
                        }
                    }
                }
            }
        }
    }
}