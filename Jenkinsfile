pipeline {
  agent any 

  options {
    timestamps()
    disableConcurrentBuilds()
    skipDefaultCheckout(false)
    buildDiscarder(logRotator(numToKeepStr: '30'))
  }

  environment {
    PROJECT_ID = 'hack-506414'
    REGION = 'us-central1'
    REPOSITORY = 'ruhuna-auction'
    IMAGE_TAG = "${env.GIT_COMMIT}"
    REGISTRY = "${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}"
    RUNTIME_SA = "ruhuna-auction-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
  }

  stages {
    stage('Verify') {
      steps {
        sh '''
          set -euxo pipefail
          java -version
          for service in api-gateway user-service auction-service bid-service notification-service; do
            (cd "$service" && ./mvnw -B test)
          done
        '''
      }
    }

    stage('Authenticate') {
      steps {
        sh '''
          set -euxo pipefail
          gcloud config set project "$PROJECT_ID"
          gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet
        '''
      }
    }

    stage('Build and push') {
      steps {
        script {
          ['api-gateway', 'user-service', 'auction-service', 'bid-service', 'notification-service'].each { service ->
            sh """
              set -euxo pipefail
              docker build --pull --file '${service}/Dockerfile' \
                --tag '${REGISTRY}/${service}:${IMAGE_TAG}' .
              docker push '${REGISTRY}/${service}:${IMAGE_TAG}'
            """
          }
        }
      }
    }

    stage('Deploy') {
      steps {
        script {
          ['api-gateway', 'user-service', 'auction-service', 'bid-service', 'notification-service'].each { service ->
            sh """
              set -euxo pipefail
              gcloud run services update 'ruhuna-auction-${service}' \
                --region '${REGION}' \
                --image '${REGISTRY}/${service}:${IMAGE_TAG}' \
                --service-account '${RUNTIME_SA}' \
                --quiet
            """
          }
        }
      }
    }

    stage('Smoke test') {
      steps {
        sh '''
          set -euxo pipefail
          GATEWAY_URL="$(gcloud run services describe "ruhuna-auction-api-gateway" \
            --region "$REGION" --format='value(uri)')"
          curl --fail --retry 5 --retry-delay 5 "$GATEWAY_URL/actuator/health"
          curl --fail --retry 5 --retry-delay 5 "$GATEWAY_URL/api/users/status"
        '''
      }
    }
  }

  post {
    always {
      junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
      archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/*.jar'
    }
  }
}
