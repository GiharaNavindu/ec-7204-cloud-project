locals {
  internal_services = {
    user-service         = { port = 8081 }
    auction-service      = { port = 8082 }
    bid-service          = { port = 8083 }
    notification-service = { port = 8085 }
  }
  image_base = "${var.region}-docker.pkg.dev/${var.project_id}/${var.name_prefix}"
  common_labels = {
    application = "ruhuna-auction"
    environment = var.environment
    managed-by  = "terraform"
  }
}

# 1. INTERNAL MICROSERVICES (Private)
resource "google_cloud_run_v2_service" "internal" {
  for_each = local.internal_services
  name     = "${var.name_prefix}-${each.key}"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_INTERNAL_ONLY"
  labels   = local.common_labels
  deletion_protection = false

  template {
    scaling {
      min_instance_count = 1
    }
    service_account = google_service_account.runtime.email
    vpc_access {
      connector = google_vpc_access_connector.serverless.id
      egress    = "ALL_TRAFFIC"
    }
    containers {
      image = "${local.image_base}/${each.key}:${var.image_tag}"
      
      ports {
        container_port = each.value.port
      }
      
      env {
        name  = "JWT_EXPIRATION"
        value = "86400000"
      }
      env {
        name  = "RABBITMQ_HOST"
        value = var.rabbitmq_host
      }
      env {
        name  = "RABBITMQ_PORT"
        value = tostring(var.rabbitmq_port)
      }
      env {
        name  = "RABBITMQ_TLS"
        value = tostring(var.rabbitmq_tls)
      }
      
      env {
        name  = "DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.postgres.private_ip_address}:5432/${replace(each.key, "-service", "_db")}?sslmode=require&sslfactory=org.postgresql.ssl.NonValidatingFactory"
      }
      env {
        name  = "NOTIFICATION_DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.postgres.private_ip_address}:5432/notification_db?sslmode=require&sslfactory=org.postgresql.ssl.NonValidatingFactory"
      }
      env {
        name  = "DB_USERNAME"
        value = var.postgres_admin_username
      }
      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.secrets["postgres-password"].secret_id
            version = "latest"
          }
        }
      }

      env {
        name  = "GOOGLE_CLIENT_ID"
        value = var.google_client_id
      }
      env {
        name = "GOOGLE_CLIENT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.secrets["google-client-secret"].secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "RABBITMQ_USERNAME"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.secrets["rabbitmq-username"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "RABBITMQ_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.secrets["rabbitmq-password"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.secrets["jwt-secret"].secret_id
            version = "latest"
          }
        }
      }
    }
  }
  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }
}

# 2. API GATEWAY (Public)
resource "google_cloud_run_v2_service" "gateway" {
  name     = "${var.name_prefix}-api-gateway"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"
  labels   = local.common_labels
  deletion_protection = false

  template {
    scaling {
      min_instance_count = 1
    }
    service_account = google_service_account.runtime.email
    vpc_access {
      connector = google_vpc_access_connector.serverless.id
      egress    = "ALL_TRAFFIC"
    }
    containers {
      image = "${local.image_base}/api-gateway:${var.image_tag}"
      
      ports {
        container_port = 8080
      }
      
      env {
        name  = "JWT_EXPIRATION"
        value = "86400000"
      }
      env {
        name  = "ALLOWED_ORIGIN_PATTERN"
        value = var.allowed_origin_pattern
      }
      
      # Map routing to internal services automatically
      env {
        name  = "USER_SERVICE_URL"
        value = google_cloud_run_v2_service.internal["user-service"].uri
      }
      env {
        name  = "AUCTION_SERVICE_URL"
        value = "https://ruhuna-auction-auction-service-pjmru6x4ba-uc.a.run.app"
      }
      env {
        name  = "BID_SERVICE_URL"
        value = google_cloud_run_v2_service.internal["bid-service"].uri
      }
      env {
        name  = "NOTIFICATION_SERVICE_URL"
        value = google_cloud_run_v2_service.internal["notification-service"].uri
      }

      env {
        name = "JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.secrets["jwt-secret"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "REDIS_HOST"
        value = google_redis_instance.rate_limit.host
      }
      env {
        name  = "REDIS_PORT"
        value = tostring(google_redis_instance.rate_limit.port)
      }
    }
  }
  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }
}

resource "google_cloud_run_service_iam_member" "public_gateway" {
  location = var.region
  service  = google_cloud_run_v2_service.gateway.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_service_iam_member" "internal_invoker" {
  for_each = local.internal_services
  location = var.region
  service  = google_cloud_run_v2_service.internal[each.key].name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
