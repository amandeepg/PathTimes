import asyncio

import src.handlers.streamlit_view_cache as mn

if __name__ == "__main__":
    # Streamlit automatically handles the event loop for top-level async functions
    asyncio.run(mn.main())
