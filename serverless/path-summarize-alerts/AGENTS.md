# Gemini Code Assistant Context

## Project Overview

This project is a serverless Python application designed to summarize text alerts, particularly for the PATH train system. It leverages Large Language Models (LLMs) to generate concise and informative summaries of alerts, which are then cached in DynamoDB to optimize for speed and cost. The system is architected to provide a quick initial summary from a fast LLM, while asynchronously gathering more detailed summaries from a wider range of models.

### Core Technologies

*   **Backend:** Python 3.12, AWS Lambda, API Gateway, DynamoDB
*   **Frameworks/Libraries:**
    *   AWS Serverless Application Model (SAM) for infrastructure as code.
    *   `baml` for interacting with various LLMs.
    *   `aws-lambda-powertools` for structured logging, tracing, and event parsing.
    *   `boto3` for AWS SDK interactions.
    *   `dyntastic` for DynamoDB data modeling and access.
    *   `jinja2` for HTML templating.
*   **Development Tools:**
    *   `ruff` for linting.
    *   `pyright` for static type checking.

### Architecture

The application consists of three main components:

1.  **`SummarizeFunction` (`/summarize`):** This is the primary public-facing API endpoint. It receives a text alert, first checks for a cached summary from a preferred LLM, and if not found, generates a new summary using a "fast" LLM. It then asynchronously invokes the `MultisummarizeFunction` to process the alert with a broader set of LLMs.

2.  **`MultisummarizeFunction`:** This background function is triggered by the `SummarizeFunction`. It takes the original alert text and generates summaries using multiple configured LLMs. The results are then stored in the DynamoDB cache for future requests.

3.  **`ViewCacheFunction` (`/viewcache`):** This endpoint provides a simple HTML interface to view the contents of the DynamoDB cache, allowing for easy inspection of the various LLM outputs for a given alert.

4.  **`AiResponsesTables` (DynamoDB Table):** A single DynamoDB table is used to cache all LLM-generated summaries. The table is designed with a primary key based on a hash of the input text and the application's code version, enabling efficient cache lookups.

## Building and Running

The project includes shell scripts for common development tasks.

*   **Compilation/Build:**
    ```bash
    # Run the compile script (to package the application for deployment)
    ./compile.sh
    ```

*   **Deployment:**
    ```bash
    # Deploy the application to AWS using the SAM CLI
    ./deploy.sh | yes
    ```
