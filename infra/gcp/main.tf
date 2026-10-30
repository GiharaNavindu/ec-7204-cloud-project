resource "google_artifact_registry_repository" "containers" {
  location      = var.region
  repository_id = var.name_prefix
  description   = "Ruhuna Auction container images"
  format        = "DOCKER"
}

resource "google_compute_network" "vpc" {
  name                    = "${var.name_prefix}-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "serverless" {
  name          = "${var.name_prefix}-subnet"
  ip_cidr_range = "10.20.0.0/24"
  region        = var.region
  network       = google_compute_network.vpc.id
}

resource "google_vpc_access_connector" "serverless" {
  name          = "${var.name_prefix}-connector"
  region        = var.region
  network       = google_compute_network.vpc.name
  ip_cidr_range = "10.8.0.0/28"
  min_instances = 2
  max_instances = 5
}

resource "google_compute_global_address" "private_services" {
  name          = "${var.name_prefix}-private-services"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.vpc.id
}

resource "google_service_networking_connection" "private_vpc" {
  network                 = google_compute_network.vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_services.name]
}

resource "google_sql_database_instance" "postgres" {
  name             = "${var.name_prefix}-postgres"
  database_version = "POSTGRES_16"
  region           = var.region
  deletion_protection = false

  settings {
    tier              = var.cloud_sql_tier
    edition           = "ENTERPRISE"
    availability_type = "ZONAL"
    disk_type         = "PD_SSD"
    disk_size         = var.cloud_sql_disk_gb
    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.vpc.id
      ssl_mode        = "ENCRYPTED_ONLY"
    }
  }
  depends_on = [google_service_networking_connection.private_vpc]
}

resource "google_sql_user" "admin" {
  name     = var.postgres_admin_username
  instance = google_sql_database_instance.postgres.name
  password = var.postgres_password
}

resource "google_sql_database" "databases" {
  for_each = toset(["user_db", "auction_db", "bid_db", "notification_db"])
  name     = each.key
  instance = google_sql_database_instance.postgres.name
}

resource "google_redis_instance" "rate_limit" {
  name               = "${var.name_prefix}-redis"
  tier               = "BASIC"
  memory_size_gb     = var.redis_memory_gb
  region             = var.region
  redis_version      = "REDIS_7_2"
  authorized_network = google_compute_network.vpc.id
  connect_mode       = "PRIVATE_SERVICE_ACCESS"
  auth_enabled       = true
  depends_on         = [google_service_networking_connection.private_vpc]
}

resource "google_service_account" "runtime" {
  account_id   = "${var.name_prefix}-runtime"
  display_name = "Ruhuna Auction Cloud Run runtime"
}

resource "google_secret_manager_secret" "secrets" {
  for_each = toset([
    "jwt-secret", "postgres-password", "rabbitmq-username",
    "rabbitmq-password", "google-client-secret"
  ])
  secret_id = "${var.name_prefix}-${each.key}"
  
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "values" {
  for_each = {
    jwt-secret            = var.jwt_secret
    postgres-password     = var.postgres_password
    rabbitmq-username     = var.rabbitmq_username
    rabbitmq-password     = var.rabbitmq_password
    google-client-secret  = var.google_client_secret
  }
  secret      = google_secret_manager_secret.secrets[each.key].id
  secret_data = each.value
}

resource "google_secret_manager_secret_iam_member" "runtime" {
  for_each = google_secret_manager_secret.secrets
  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime.email}"
}
