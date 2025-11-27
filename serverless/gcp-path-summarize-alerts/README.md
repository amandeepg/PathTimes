# GCP Summarize Function (Vertex + Firestore + Terraform)

Serverless HTTP Cloud Function that summarizes text with Gemini and caches results in Firestore. Includes Terraform for staging/prod deployments, TTL-based caching, and an async “pro” worker triggered via Cloud Tasks.

## Prereqs
- gcloud CLI authenticated (`gcloud auth login`).
- Application Default Credentials for Terraform: `gcloud auth application-default login` **or** pass an access token (below).
- Terraform 1.6+ (uses the globally installed `terraform` binary).
- Firestore Native database exists in the project.
- Secret Manager secret with your OpenAI key (default name `openai-api-key`):
  ```
  gcloud secrets create openai-api-key --replication=automatic
  echo -n "$OPENAI_API_KEY" | gcloud secrets versions add openai-api-key --data-file=-
  ```
  Override the secret name via `-var="openai_api_key_secret=your-secret-name"` if you prefer a different name.

## Python deps (local dev/testing)
```
uv sync   # installs into .venv from pyproject.toml (uses .python-version for 3.11)
source .venv/bin/activate
uv run pyright          # type check
uv run basedpyright     # stricter type check
uv run ruff check       # lint
uv run ruff format      # format (safe because it formats in place)
```

## Terraform deploy (staging example)
```
# Export a fresh requirements.txt for Cloud Functions (uses uv.lock, not committed)
uv export --no-dev --frozen --format requirements-txt --no-hashes --output-file requirements.txt

terraform init
terraform workspace new staging || terraform workspace select staging
# Allow a long timeout (deploy can take a few minutes); bump your CLI timeout to >= 10m.
GOOGLE_OAUTH_ACCESS_TOKEN=$(gcloud auth print-access-token) terraform apply \
  -auto-approve \
  -var="project_id=underthehudson" \
  -var="region=us-central1" \
  -var="environment=staging"
```
Outputs include:
- `function_url` (HTTP endpoint)
- `run_service_uri` (Cloud Run backing service)
- `source_bucket`
- `pro_queue` (Cloud Tasks queue for pro worker)

## Browser test
```
https://us-central1-underthehudson.cloudfunctions.net/summarize-staging?text=Your%20text%20here
```
Returns JSON with fields: `input`, `summary`, `cached`, `model`, `ttl_seconds`, `upgrading_to_expensive` (when applicable).

Response also includes `result`, which follows the Pydantic model:
```
AlertSummaryContainer(
  text: str,
  affected_stations: AffectedStations | None,  # {affected_stations: [PathStation]}
  affected_lines: AffectedLines | None,        # {affected_lines: [PathLine]}
  summary_cost: Decimal | None,
  affected_area_cost: Decimal | None,
)
```

## Prod deployment
Use a separate workspace/state and environment label:
```
# Export requirements.txt first (same as staging) before applying.
terraform workspace new prod || terraform workspace select prod
GOOGLE_OAUTH_ACCESS_TOKEN=$(gcloud auth print-access-token) terraform apply \
  -auto-approve \
  -var="project_id=underthehudson" \
  -var="region=us-central1" \
  -var="environment=prod"
```

## API tips
- `cache_key_prefix` query/body param busts cache (e.g., `?text=...&cache_key_prefix=demo123`).
- Cheap call returns immediately and enqueues pro upgrade; re-call with the same prefix to see cached responses.

## Logs and tracing
- Cloud Run service name is suffixed by workspace (e.g., `summarize-staging`). View recent LLM call logs:
  ```
  gcloud logging read 'resource.type="cloud_run_revision" resource.labels.service_name="summarize-staging" textPayload:"llm_call"' --limit=20 --format="value(textPayload)"
  ```
- Filter all request events:
  ```
  gcloud logging read 'resource.type="cloud_run_revision" resource.labels.service_name="summarize-staging" textPayload:"request_complete"' --limit=20 --format="value(textPayload)"
  ```

## Notes
- APIs enabled automatically: Cloud Functions, Run, Artifact Registry, Cloud Build, Vertex AI, Firestore, Cloud Tasks.
- Buckets are environment-scoped and auto-destroyed with state (`force_destroy=true`).
- Function names are suffixed with `environment` (e.g., `summarize-staging`, `summarize-pro-worker-staging`).
- If you prefer ADC instead of an access token, run `gcloud auth application-default login` once locally.
