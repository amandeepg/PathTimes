output "function_url" {
  description = "Public HTTPS endpoint for summarize (Cloud Run)."
  value       = "${google_cloud_run_v2_service.app.uri}/summarize"
}

output "cache_function_url" {
  description = "Public HTTPS endpoint for the cache viewer."
  value       = "${google_cloud_run_v2_service.app.uri}/cache"
}

output "environment" {
  description = "Deployment environment label."
  value       = var.environment
}

output "run_service_uri" {
  description = "Underlying Cloud Run service URI."
  value       = google_cloud_run_v2_service.app.uri
}

output "source_bucket" {
  description = "Bucket storing the zipped function source."
  value       = google_storage_bucket.function_bucket.name
}

output "pro_queue" {
  description = "Cloud Tasks queue for pro summaries."
  value       = google_cloud_tasks_queue.pro_queue.name
}
