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
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
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
  environment        = var.environment
  app_name           = "summarize-all-${var.environment}"
  queue_name         = "${var.environment}-${var.pro_queue_id}"
  build_dir          = "${path.module}/build"
  source_dir         = path.module
  archive_name       = "function.zip"
  image_tag          = "${var.environment}-${substr(data.archive_file.function_zip.output_md5, 0, 8)}"
  image_name         = "${var.region}-docker.pkg.dev/${var.project_id}/gcf-artifacts/summarize-all:${local.image_tag}"
  service_account    = "${data.google_project.current.number}-compute@developer.gserviceaccount.com"
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

resource "null_resource" "build_image" {
  # Build a single container image and push to Artifact Registry.
  triggers = {
    source_md5 = data.archive_file.function_zip.output_md5
  }
  provisioner "local-exec" {
    command = <<EOT
gcloud builds submit --timeout=1200s --tag ${local.image_name} .
EOT
  }

  depends_on = [google_project_service.apis, google_storage_bucket_object.function_source]
}

resource "google_cloud_run_v2_service" "app" {
  name     = local.app_name
  location = var.region

  template {
    service_account = local.service_account
    containers {
      image = local.image_name
      ports { container_port = 8080 }
      resources {
        limits = {
          memory = "1Gi"
        }
      }
      env {
        name  = "PROJECT_ID"
        value = var.project_id
      }
      env {
        name  = "LOCATION"
        value = var.region
      }
      env {
        name  = "ENVIRONMENT"
        value = var.environment
      }
      env {
        name  = "PRO_QUEUE_ID"
        value = local.queue_name
      }
      env {
        name  = "PRO_WORKER_URL"
        value = ""
      }
      env {
        name = "OPENAI_API_KEY"
        value_source {
          secret_key_ref {
            secret  = var.openai_api_key_secret
            version = "latest"
          }
        }
      }
    }
    scaling {
      max_instance_count = 5
    }
  }

  traffic {
    percent = 100
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
  }

  depends_on = [null_resource.build_image, google_project_service.apis]
}

resource "google_cloud_run_service_iam_member" "app_invoker" {
  provider = google-beta

  location = var.region
  service  = google_cloud_run_v2_service.app.name
  role     = "roles/run.invoker"
  member   = "allUsers"

  depends_on = [google_cloud_run_v2_service.app]
}

resource "google_secret_manager_secret_iam_member" "openai_accessor" {
  secret_id = "projects/${var.project_id}/secrets/${var.openai_api_key_secret}"
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${local.service_account}"
}
