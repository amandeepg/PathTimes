variable "project_id" {
  description = "GCP project ID to deploy the function."
  type        = string
}

variable "environment" {
  description = "Deployment environment label (e.g., staging, prod)."
  type        = string
  default     = "staging"
}

variable "region" {
  description = "Region for Cloud Functions/Run/Firestore."
  type        = string
  default     = "us-central1"
}

variable "required_apis" {
  description = "APIs to ensure are enabled for this deployment."
  type        = list(string)
  default = [
    "cloudfunctions.googleapis.com",
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "cloudbuild.googleapis.com",
    "aiplatform.googleapis.com",
    "firestore.googleapis.com",
    "cloudtasks.googleapis.com",
    "secretmanager.googleapis.com",
  ]
}

variable "pro_queue_id" {
  description = "Cloud Tasks queue ID for expensive LLM jobs."
  type        = string
  default     = "summarize-pro"
}

variable "openai_api_key_secret" {
  description = "Secret Manager secret name that stores the OpenAI API key."
  type        = string
  default     = "openai-api-key"
}
