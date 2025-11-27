
terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 4.50.0"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

# Cloud Storage bucket to store the zipped source code for the Cloud Functions
resource "google_storage_bucket" "source_bucket" {
  name          = "${var.gcp_project_id}-source"
  location      = var.gcp_region
  force_destroy = true # Set to false in production
}

# IAM for Cloud Functions
resource "google_project_iam_member" "dataflow_worker" {
  project = var.gcp_project_id
  role    = "roles/dataflow.worker"
  member  = "serviceAccount:${google_service_account.summarize_function_sa.email}"
}

resource "google_project_iam_member" "run_invoker" {
  project = var.gcp_project_id
  role    = "roles/run.invoker"
  member  = "serviceAccount:${google_service_account.summarize_function_sa.email}"
}

resource "google_project_iam_member" "storage_object_admin" {
  project = var.gcp_project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.summarize_function_sa.email}"
}

# Service account for the summarize function
resource "google_service_account" "summarize_function_sa" {
  account_id   = "summarize-function-sa"
  display_name = "Service Account for summarize-function"
}

# Firestore database
resource "google_firestore_database" "database" {
  project    = var.gcp_project_id
  name       = "(default)"
  location_id = var.gcp_region
  type       = "FIRESTORE_NATIVE"

  delete_protection_state = "DELETE_PROTECTION_DISABLED" # Set to DELETE_PROTECTION_ENABLED in production
}

# Archive the source code for the functions
data "archive_file" "summarize_source" {
  type        = "zip"
  source_dir  = "${path.module}/src"
  output_path = "${path.module}/summarize.zip"
}

# Upload the zipped source code to the Cloud Storage bucket
resource "google_storage_bucket_object" "summarize_source_zip" {
  name   = "summarize.zip"
  bucket = google_storage_bucket.source_bucket.name
  source = data.archive_file.summarize_source.output_path
}

# Cloud Function for summarize
resource "google_cloudfunctions2_function" "summarize_function" {
  name     = "summarize-function"
  location = var.gcp_region
  project  = var.gcp_project_id

  build_config {
    runtime     = "python312"
    entry_point = "handlers.summarize.handle"
    source {
      storage_source {
        bucket = google_storage_bucket.source_bucket.name
        object = google_storage_bucket_object.summarize_source_zip.name
      }
    }
  }

  service_config {
    max_instance_count = 1
    min_instance_count = 0
    available_memory   = "512Mi"
    timeout_seconds    = 60
    service_account_email = google_service_account.summarize_function_sa.email
    environment_variables = {
      OPENROUTER_API_KEY    = var.open_router_api_key
      SKIP_CACHE_MAGIC_WORD = var.skip_cache_magic_word
      OPENROUTER_APP_NAME   = var.open_router_app_name
      OPENROUTER_APP_URL    = var.open_router_app_url
      GCP_PROJECT           = var.gcp_project_id
      GCP_REGION            = var.gcp_region
      FIRESTORE_TABLE       = google_firestore_database.database.name
    }
  }
}

# API Gateway
resource "google_api_gateway_api" "api" {
  provider = google-beta
  project  = var.gcp_project_id
  api_id   = "path-summarize-api"
}

resource "google_api_gateway_api_config" "api_config" {
  provider      = google-beta
  project       = var.gcp_project_id
  api           = google_api_gateway_api.api.api_id
  api_config_id = "path-summarize-config"

  openapi_documents {
    document {
      path     = "spec.yaml"
      contents = filebase64("${path.module}/spec.yaml")
    }
  }

  gateway_config {
    backend_config {
      google_service_account = google_service_account.summarize_function_sa.email
    }
  }
}

resource "google_api_gateway_gateway" "gateway" {
  provider    = google-beta
  project     = var.gcp_project_id
  region      = var.gcp_region
  gateway_id  = "path-summarize-gateway"
  api_config  = google_api_gateway_api_config.api_config.id
}
