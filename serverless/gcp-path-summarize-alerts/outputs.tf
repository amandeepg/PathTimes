output "function_url" {
  description = "Public HTTPS endpoint for the summarize function."
  value       = google_cloudfunctions2_function.summarize.url
}

output "environment" {
  description = "Deployment environment label."
  value       = var.environment
}

output "run_service_uri" {
  description = "Underlying Cloud Run service URI."
  value       = google_cloudfunctions2_function.summarize.service_config[0].uri
}

output "source_bucket" {
  description = "Bucket storing the zipped function source."
  value       = google_storage_bucket.function_bucket.name
}

output "pro_queue" {
  description = "Cloud Tasks queue for pro summaries."
  value       = google_cloud_tasks_queue.pro_queue.name
}
