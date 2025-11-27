terraform {
  required_version = ">= 1.6.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.14"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 6.14"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

data "google_project" "current" {
  project_id = var.project_id
}

locals {
  environment       = var.environment
  function_name     = "summarize-${var.environment}"
  pro_function_name = "summarize-pro-worker-${var.environment}"
  queue_name        = "${var.environment}-${var.pro_queue_id}"
  build_dir         = "${path.module}/build"
  source_dir        = path.module
  archive_name      = "function.zip"
}

resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "google_project_service" "apis" {
  for_each           = toset(var.required_apis)
  project            = var.project_id
  service            = each.key
  disable_on_destroy = false
}

resource "google_storage_bucket" "function_bucket" {
  name                        = "${var.project_id}-${local.environment}-summarize-${random_id.bucket_suffix.hex}"
  location                    = var.region
  uniform_bucket_level_access = true
  force_destroy               = true
}

data "archive_file" "function_zip" {
  type        = "zip"
  source_dir  = local.source_dir
  output_path = "${local.build_dir}/${local.archive_name}"
  excludes = [
    ".terraform",
    ".terraform.lock.hcl",
    "build",
    ".venv",
    "venv",
    "env",
    ".git",
    ".gcloudignore",
    "terraform.tfstate",
    "terraform.tfstate.backup",
  ]
}

resource "google_storage_bucket_object" "function_source" {
  name   = "sources/${local.archive_name}"
  bucket = google_storage_bucket.function_bucket.name
  source = data.archive_file.function_zip.output_path
}

resource "google_cloud_tasks_queue" "pro_queue" {
  name     = local.queue_name
  location = var.region
}

resource "google_cloudfunctions2_function" "summarize" {
  name     = local.function_name
  location = var.region

  build_config {
    runtime     = "python311"
    entry_point = "summarize"
    source {
      storage_source {
        bucket     = google_storage_bucket.function_bucket.name
        object     = google_storage_bucket_object.function_source.name
        generation = google_storage_bucket_object.function_source.generation
      }
    }
  }

  service_config {
    available_memory = "512M"
    environment_variables = {
      LOCATION       = var.region
      ENVIRONMENT    = var.environment
      PRO_WORKER_URL = google_cloudfunctions2_function.summarize_pro_worker.url
      PRO_QUEUE_ID   = local.queue_name
    }
    secret_environment_variables {
      key     = "OPENAI_API_KEY"
      project_id = var.project_id
      secret  = var.openai_api_key_secret
      version = "latest"
    }
    ingress_settings = "ALLOW_ALL"
  }

  depends_on = [google_project_service.apis]
}

resource "google_cloud_run_service_iam_member" "invoker" {
  provider = google-beta

  location = var.region
  service  = google_cloudfunctions2_function.summarize.service_config[0].service
  role     = "roles/run.invoker"
  member   = "allUsers"

  depends_on = [google_cloudfunctions2_function.summarize]
}

resource "google_cloudfunctions2_function" "summarize_pro_worker" {
  name     = local.pro_function_name
  location = var.region

  build_config {
    runtime     = "python311"
    entry_point = "summarize_pro_worker_entrypoint"
    source {
      storage_source {
        bucket     = google_storage_bucket.function_bucket.name
        object     = google_storage_bucket_object.function_source.name
        generation = google_storage_bucket_object.function_source.generation
      }
    }
  }

  service_config {
    available_memory = "512M"
    environment_variables = {
      LOCATION     = var.region
      ENVIRONMENT  = var.environment
      PRO_QUEUE_ID = local.queue_name
    }
    secret_environment_variables {
      key     = "OPENAI_API_KEY"
      project_id = var.project_id
      secret  = var.openai_api_key_secret
      version = "latest"
    }
    ingress_settings = "ALLOW_ALL"
  }

  depends_on = [google_project_service.apis]
}

resource "google_cloud_run_service_iam_member" "pro_worker_invoker" {
  provider = google-beta

  location = var.region
  service  = google_cloudfunctions2_function.summarize_pro_worker.service_config[0].service
  role     = "roles/run.invoker"
  member   = "allUsers"

  depends_on = [google_cloudfunctions2_function.summarize_pro_worker]
}

resource "google_secret_manager_secret_iam_member" "openai_accessor" {
  secret_id = "projects/${var.project_id}/secrets/${var.openai_api_key_secret}"
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${data.google_project.current.number}-compute@developer.gserviceaccount.com"
}
