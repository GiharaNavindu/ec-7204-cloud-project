pipeline {
  agent any 

  options {
    timestamps()
    disableConcurrentBuilds()
    skipDefaultCheckout(false)
    buildDiscarder(logRotator(numToKeepStr: '15'))
    timeout(time: 30, unit: 'MINUTES') // Fail-safe: Prevent hung jobs from locking the server
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
      environment {
        SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID = 'mock-client-id'
        SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET = 'mock-client-secret'
        JWT_EXPIRATION = '60000'
      }
      steps {
        sh '''
          set -euxo pipefail
          java -version
          for service in api-gateway user-service auction-service bid-service notification-service; do
            echo "Running tests for $service..."
            # Fail-safe: Ensure executable permissions before running Maven
            (cd "$service" && chmod +x mvnw && ./mvnw -B clean test)
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
          def services = ['api-gateway', 'user-service', 'auction-service', 'bid-service', 'notification-service']
          services.each { service ->
            sh """
              set -euxo pipefail
              echo "Building ${service}..."
              docker build --pull --file '${service}/Dockerfile' \
                --tag '${REGISTRY}/${service}:${IMAGE_TAG}' \
                --tag '${REGISTRY}/${service}:latest' .
              
              echo "Pushing ${service}..."
              docker push '${REGISTRY}/${service}:${IMAGE_TAG}'
              docker push '${REGISTRY}/${service}:latest'
            """
          }
        }
      }
    }

    stage('Deploy') {
      steps {
        script {
          def services = ['api-gateway', 'user-service', 'auction-service', 'bid-service', 'notification-service']
          services.each { service ->
            sh """
              set -euxo pipefail
              echo "Deploying ${service} to Cloud Run..."
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
          
          echo "Testing Gateway URL: $GATEWAY_URL"
          # Fail-safe: Allow Cloud Run load balancers to shift traffic before testing
          sleep 10
          
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
      
      // Fail-safe: Prune old Docker images so the Jenkins VM disk does not fill up and crash
      sh 'docker system prune -af --filter "until=24h" || true'
    }
    success {
      echo "Pipeline completed successfully! Cloud Run has been updated."
    }
    failure {
      echo "Pipeline failed. Check the logs above."
    }
  }
}
