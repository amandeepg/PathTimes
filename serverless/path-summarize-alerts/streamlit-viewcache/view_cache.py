import asyncio
import logging
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple

import boto3
import pandas as pd
import streamlit as st
from botocore.client import BaseClient
from botocore.exceptions import ClientError, NoCredentialsError

from baml_client.types import AffectedStations, AffectedRoutes
from lib.cache import CacheService
from lib.constants import BUCKET_NAME
from lib.llm_clients import ALL_LLM_CLIENTS
from lib.models import CacheResponse

# --- Configuration & Constants ---
APP_TITLE = "LLM Output Cache Viewer"
APP_ICON = "📊"
MAX_FILES_TO_PROCESS = 500  # Limit processing to avoid performance issues
CACHE_TTL_SECONDS = 120  # Cache data for 2 minutes


# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


# --- Caching Setup ---
# Use Streamlit's caching decorators for efficiency


@st.cache_resource(ttl=timedelta(hours=1))
def get_s3_client() -> Optional[BaseClient]:
    """Creates and caches an S3 client."""
    logger.info("Attempting to create S3 client...")
    try:
        # Use Streamlit secrets first for credentials
        aws_access_key_id = st.secrets.get("AWS_ACCESS_KEY_ID")
        aws_secret_access_key = st.secrets.get("AWS_SECRET_ACCESS_KEY")
        aws_region = st.secrets.get(
            "AWS_DEFAULT_REGION", "us-east-1"
        )  # Default if not set

        if aws_access_key_id and aws_secret_access_key:
            logger.info("Using AWS credentials from Streamlit secrets.")
            client = boto3.client(
                "s3",
                aws_access_key_id=aws_access_key_id,
                aws_secret_access_key=aws_secret_access_key,
                region_name=aws_region,
            )
        else:
            logger.info(
                "Using default AWS credentials provider chain (environment variables, IAM role, etc.)."
            )
            # If secrets aren't available, boto3 uses the default credential chain
            client = boto3.client(
                "s3", region_name=aws_region
            )  # Specify region even for default chain

        # Test connection/credentials
        client.list_buckets()
        logger.info("S3 client created successfully.")
        return client

    except NoCredentialsError:
        st.error(
            "AWS credentials not found. Please configure them via Streamlit secrets, environment variables, or IAM role."
        )
        logger.error("AWS credentials not found.")
        return None
    except ClientError as e:
        st.error(f"AWS Client Error: {e}. Check credentials and permissions.")
        logger.error(f"AWS Client Error: {e}")
        return None
    except Exception as e:
        st.error(f"Unexpected error creating S3 client: {e}")
        logger.exception(f"Unexpected error creating S3 client: {e}")
        return None


@st.cache_resource(ttl=timedelta(hours=1))
def get_cache_service() -> Optional[CacheService]:
    """Creates and caches a CacheService instance."""
    logger.info("Creating CacheService instance.")
    if not BUCKET_NAME:
        st.error(
            "`BUCKET_NAME` is not defined in `src.lib.constants`. Please define it."
        )
        logger.error("BUCKET_NAME is not defined.")
        return None
    try:
        service = CacheService(BUCKET_NAME)
        logger.info("CacheService instance created successfully.")
        return service
    except Exception as e:
        st.error(f"Error creating CacheService: {e}")
        logger.exception(f"Error creating CacheService: {e}")
        return None


# --- Data Fetching and Processing ---


async def list_s3_objects(bucket: str, prefix: str) -> List[Dict[str, Any]]:
    """
    Lists objects in the S3 bucket with the given prefix.
    """
    s3_client = get_s3_client()  # Get cached client
    if not s3_client:
        return []

    logger.info(f"Listing objects in s3://{bucket}/{prefix}")
    all_contents = []
    try:
        paginator = s3_client.get_paginator("list_objects_v2")
        pages = paginator.paginate(Bucket=bucket, Prefix=prefix)
        for page in pages:
            if "Contents" in page:
                all_contents.extend(page["Contents"])

        # Sort by LastModified descending (newest first)
        sorted_contents = sorted(
            all_contents,
            key=lambda x: x.get("LastModified", datetime.min),
            reverse=True,
        )
        logger.info(f"Found {len(sorted_contents)} objects in s3://{bucket}/{prefix}")
        return sorted_contents
    except ClientError as e:
        st.error(
            f"Error listing S3 objects: {e}. Check bucket name, prefix, and permissions."
        )
        logger.error(
            f"Error listing S3 objects for bucket {bucket}, prefix {prefix}: {e}"
        )
        return []
    except Exception as e:
        st.error(f"Unexpected error listing S3 objects: {e}")
        logger.exception(f"Unexpected error listing S3 objects: {e}")
        return []

async def fetch_and_parse_s3_object(key: str) -> CacheResponse | None:
    """Fetches and parses a single S3 object asynchronously."""
    cache_service = get_cache_service()
    if not cache_service:
        return None

    prefix_to_remove = f"{cache_service.hash_category_key()}/"
    object_identifier = key.replace(prefix_to_remove, "")
    try:
        file_content = await asyncio.to_thread(get_from_cache, object_identifier)
        if file_content:
            try:
                return CacheResponse.model_validate_json(file_content)
            except Exception as parse_error:
                logger.error(f"Failed to parse JSON for key {key}: {parse_error}")
                return None
        else:
            logger.warning(f"Empty content for key: {key}")
            return None
    except ClientError as e:
        logger.error(f"S3 client error fetching {key}: {e}")
        return None
    except Exception as e:
        logger.error(f"Error processing file {key}: {str(e)}")
        return None


@st.cache_data(ttl=timedelta(hours=1))
def get_from_cache(object_identifier: str) -> Optional[str]:
    return get_cache_service().get(object_identifier)


async def fetch_all_data(
    object_keys: Tuple[str, ...],
) -> List[CacheResponse]:
    """Fetches and parses multiple S3 objects concurrently."""
    s3_client = get_s3_client()
    cache_service = get_cache_service()
    bucket = BUCKET_NAME

    if not s3_client or not cache_service or not bucket:
        st.error(
            "S3 client, Cache Service, or Bucket Name not available for fetching data."
        )
        return []

    logger.info(f"Fetching content for {len(object_keys)} keys...")
    # Use a progress bar
    progress_bar = st.progress(0.0, text="Fetching data from S3...")
    tasks = []
    results = []

    # Limit concurrency slightly if needed, though asyncio.gather handles it well
    # BATCH_SIZE = 100 # Example

    for i, key in enumerate(object_keys):
        tasks.append(fetch_and_parse_s3_object(key))
        # Process in batches or update progress periodically
        if (i + 1) % 50 == 0 or i == len(object_keys) - 1:
            batch_results = await asyncio.gather(*tasks)
            results.extend(batch_results)
            tasks = []
            progress = (i + 1) / len(object_keys)
            progress_bar.progress(
                progress, text=f"Fetching data... ({i + 1}/{len(object_keys)})"
            )

    progress_bar.empty()  # Clear progress bar

    valid_results = [result for result in results if result is not None]
    logger.info(
        f"Successfully parsed {len(valid_results)} out of {len(object_keys)} fetched objects."
    )
    if len(valid_results) < len(object_keys):
        logger.warning(
            f"{len(object_keys) - len(valid_results)} objects failed to parse or were empty."
        )

    return valid_results


def process_and_group_data(
    files_data: List[CacheResponse],
) -> Dict[str, List[CacheResponse]]:
    """Groups CacheResponse objects by input text and filters invalid entries."""
    if not files_data:
        return {}

    logger.info(f"Processing {len(files_data)} fetched responses...")
    # Get valid model IDs for filtering
    try:
        all_llm_client_ids = {client.id() for client in ALL_LLM_CLIENTS}
    except NameError:
        st.warning("`ALL_LLM_CLIENTS` not defined, cannot filter by valid models.")
        logger.warning("ALL_LLM_CLIENTS not defined.")
        all_llm_client_ids = None

    files_by_input: Dict[str, List[CacheResponse]] = {}
    valid_count = 0
    filtered_test_count = 0
    filtered_model_count = 0
    filtered_type_count = 0

    for data in files_data:
        # Filter invalid types
        if not isinstance(data, CacheResponse):
            filtered_type_count += 1
            continue
        # Filter test inputs (case-insensitive)
        if "testinput" in data.input.lower():
            filtered_test_count += 1
            continue
        # Filter unknown models if client list is available
        if all_llm_client_ids and data.model not in all_llm_client_ids:
            filtered_model_count += 1
            continue

        # Group by input text
        if data.input not in files_by_input:
            files_by_input[data.input] = []
        files_by_input[data.input].append(data)
        valid_count += 1

    # Sort groups by the generated_at timestamp of the first item in each group (newest first)
    # And sort items within each group alphabetically by model name
    sorted_groups = {}
    # Ensure groups are not empty before accessing index 0
    group_keys_sorted = sorted(
        (
            k for k, v in files_by_input.items() if v
        ),  # Filter out empty groups just in case
        key=lambda k: files_by_input[k][0].generated_at or 0,
        reverse=True,
    )

    for key in group_keys_sorted:
        sorted_groups[key] = sorted(files_by_input[key], key=lambda x: x.model)

    logger.info(
        f"Processing complete: {valid_count} valid entries grouped into {len(sorted_groups)} inputs. "
    )

    return sorted_groups


# --- Helper Functions for UI and Formatting ---


def _format_generated_date(epoch: Optional[int]) -> str:
    """Formats epoch timestamp into a readable string (e.g., Aug 15, 2023 03:05 PM)."""
    if epoch is None:
        return "N/A"
    try:
        dt = datetime.fromtimestamp(epoch)
        return dt.strftime("%b %d, %Y %I:%M %p").replace(" 0", " ")
    except Exception:
        return "Invalid Date"


def _format_affected_area(
    affected_area: Optional[AffectedRoutes | AffectedStations | None],
) -> str:
    """Formats the AffectedArea object into a concise, readable string."""
    if not affected_area:
        return "None"

    parts = []
    # Check for specific types if using Pydantic models or similar
    if isinstance(affected_area, AffectedRoutes) and affected_area.affected_routes:
        routes = ", ".join(
            sorted(
                str(r).replace("PathLine.", "") for r in affected_area.affected_routes
            )
        )
        parts.append(f"**Routes:** {routes}")
    elif (
        isinstance(affected_area, AffectedStations) and affected_area.affected_stations
    ):
        stations = ", ".join(
            sorted(
                str(s).replace("PathStation.", "")
                for s in affected_area.affected_stations
            )
        )
        parts.append(f"**Stations:** {stations}")
    # Fallback for less specific types or unexpected structures
    elif hasattr(affected_area, "affected_routes") and affected_area.affected_routes:
        routes = ", ".join(
            sorted(
                str(r).replace("PathLine.", "") for r in affected_area.affected_routes
            )
        )
        parts.append(f"**Routes:** {routes}")
    elif (
        hasattr(affected_area, "affected_stations") and affected_area.affected_stations
    ):
        stations = ", ".join(
            sorted(
                str(s).replace("PathStation.", "")
                for s in affected_area.affected_stations
            )
        )
        parts.append(f"**Stations:** {stations}")

    return "; ".join(parts) if parts else "Specific area not identified"


def format_delay_status(is_delay: Optional[bool]) -> str:
    """Returns an icon/styled text for delay status."""
    if is_delay is None:
        return "❓ Unknown"
    return "🚨 Yes" if is_delay else "✅ No"


def apply_filters(
    data: Dict[str, List[CacheResponse]],
    selected_models: List[str],
    delay_filter: str,
    search_query: str,
) -> Dict[str, List[CacheResponse]]:
    """Filters the grouped data based on sidebar selections."""
    filtered_data = {}
    if not data:
        return {}

    search_query_lower = search_query.lower()

    for input_text, responses in data.items():
        # Apply search query filter first (on input text)
        if search_query_lower and search_query_lower not in input_text.lower():
            continue

        filtered_responses = []
        for resp in responses:
            # Apply model filter
            if selected_models and resp.model not in selected_models:
                continue

            # Apply delay filter
            is_delay = resp.response.is_delay if resp.response else None
            if delay_filter == "Yes" and not is_delay:
                continue
            if delay_filter == "No" and is_delay:
                continue
            # 'All' or unknown status passes

            filtered_responses.append(resp)

        # Only include the input group if it still has responses after filtering
        if filtered_responses:
            filtered_data[input_text] = filtered_responses

    return filtered_data


# --- Streamlit UI Rendering ---


def render_response_details(response_data: CacheResponse):
    """Renders the details for a single CacheResponse in a card-like format."""
    col1, col2, col3 = st.columns([2, 1, 4])
    with col1:
        st.info(f"**Model:** `{response_data.model}`")
        st.caption(f"Generated: {_format_generated_date(response_data.generated_at)}")
    with col2:
        delay_status = (
            response_data.response.is_delay if response_data.response else None
        )
        st.metric(
            "Delay Reported?", "Yes" if delay_status else "No", delta_color="inverse"
        )
        # st.markdown(f"**Delay:** {format_delay_status(delay_status)}")
    with col3:
        affected_area_str = _format_affected_area(
            response_data.response.affected_area if response_data.response else None
        )
        st.markdown(f"**Affected Area:** {affected_area_str}")

    st.markdown("**Summary:**")
    st.markdown(
        f"> {response_data.response.text}" if response_data.response else "> N/A"
    )
    st.divider()


async def main():
    st.set_page_config(page_title=APP_TITLE, page_icon=APP_ICON, layout="wide")

    # --- Header ---
    st.title(f"{APP_ICON} {APP_TITLE}")

    # --- Initialize Services ---
    s3_client = get_s3_client()
    cache_service = get_cache_service()

    if not s3_client or not cache_service:
        st.error(
            "Failed to initialize AWS services. Cannot proceed. Check logs and credentials."
        )
        st.stop()

    prefix = cache_service.hash_category_key()

    # --- Sidebar for Controls ---
    st.sidebar.header("⚙️ Controls & Filters")

    st.sidebar.caption(f"Bucket: `{BUCKET_NAME}`\n\nPrefix: `{prefix}`")
    st.sidebar.divider()

    # --- Data Loading ---
    # Perform data loading steps sequentially with spinners
    with st.spinner("Listing cached files from S3..."):
        s3_objects = await list_s3_objects(
            BUCKET_NAME, prefix
        )  # Pass client for cache key

    if not s3_objects:
        st.warning(f"No files found in `s3://{BUCKET_NAME}/{prefix}`.")
        st.info(
            "Possible reasons: Cache is empty, prefix/bucket name is incorrect, or insufficient S3 permissions."
        )
        st.stop()

    total_files = len(s3_objects)
    keys_to_fetch = tuple(
        obj["Key"] for obj in s3_objects
    )  # Use tuple for caching


    # Fetch and parse data - spinner handled inside fetch_all_data now
    all_files_data = await fetch_all_data(
        keys_to_fetch
    )

    if not all_files_data:
        st.warning("No valid data could be fetched or parsed from S3.")
        st.stop()

    # Process and Group Data
    with st.spinner("Grouping and processing data..."):
        grouped_data = process_and_group_data(all_files_data)

    if not grouped_data:
        st.warning(
            "No valid, non-test data found after initial processing and grouping."
        )
        st.stop()

    # --- Filtering ---
    st.sidebar.subheader("Filters")

    # Get unique model names from the processed data for the filter
    all_models = sorted(
        list(
            set(resp.model for responses in grouped_data.values() for resp in responses)
        )
    )
    selected_models = st.sidebar.multiselect(
        "Filter by Model", options=all_models, default=[]
    )

    delay_filter = st.sidebar.selectbox(
        "Filter by Delay Status", options=["All", "Yes", "No"], index=0
    )

    search_query = st.sidebar.text_input(
        "Search Input Text", placeholder="Enter keywords..."
    )

    # Apply filters
    filtered_grouped_data = apply_filters(
        grouped_data, selected_models, delay_filter, search_query
    )

    # --- Main Content Area ---
    total_groups = len(filtered_grouped_data)

    if total_groups == 0:
        st.warning("No data matches the current filter criteria.")
    else:
        # Display grouped data using expanders
        for i, (input_text, data_group) in enumerate(filtered_grouped_data.items()):
            first_item_date_str = _format_generated_date(data_group[0].generated_at)
            expander_label = (
                f"({first_item_date_str}): "
                f"{input_text[:80]}{'...' if len(input_text) > 80 else ''}"
            )

            with st.expander(expander_label):
                st.text(input_text)

                display_data = []
                for data in data_group:
                    display_data.append(
                        {
                            "Model": data.model,
                            "Summary": data.response.text if data.response else "N/A",
                            "Is Delay": format_delay_status(
                                data.response.is_delay if data.response else None
                            ),
                            "Affected Area": _format_affected_area(
                                data.response.affected_area if data.response else None
                            ),
                        }
                    )
                df = pd.DataFrame(display_data)
                st.dataframe(
                    df,
                    column_config={
                        "Model": st.column_config.TextColumn(width="small"),
                        "Is Delay": st.column_config.TextColumn(
                            "Delay?", width="small"
                        ),
                        "Generated At": st.column_config.TextColumn(width="medium"),
                        "Affected Area": st.column_config.TextColumn(width="medium"),
                        "Summary": st.column_config.TextColumn(width="large"),
                    },
                    hide_index=True,
                    use_container_width=True,
                )


if __name__ == "__main__":
    # Streamlit automatically handles the event loop for top-level async functions
    asyncio.run(main())
