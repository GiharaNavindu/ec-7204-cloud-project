output "gateway_url" {
  value = google_cloud_run_v2_service.gateway.uri
}

output "internal_service_urls" {
  value = {
    for service in ["user-service", "auction-service", "bid-service", "notification-service"] :
    service => google_cloud_run_v2_service.internal[service].uri
  }
}

output "artifact_registry" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${var.name_prefix}"
}
