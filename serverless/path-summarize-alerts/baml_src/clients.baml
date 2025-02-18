// Learn more about clients at https://docs.boundaryml.com/docs/snippets/clients/overview

client<llm> V3 {
  provider "openai-generic"
  retry_policy Constant
  options {
    base_url "https://openrouter.ai/api/v1"
    api_key env.OPENROUTER_API_KEY
    model "deepseek/deepseek-chat"
    headers {
      "HTTP-Referer" env.OPENROUTER_APP_URL
      "X-Title" env.OPENROUTER_APP_NAME
    }
  }
}

client<llm> R1 {
  provider "openai-generic"
  retry_policy Constant
  options {
    base_url "https://openrouter.ai/api/v1"
    api_key env.OPENROUTER_API_KEY
    model "deepseek/deepseek-r1"
    headers {
      "HTTP-Referer" env.OPENROUTER_APP_URL
      "X-Title" env.OPENROUTER_APP_NAME
    }
  }
}

client<llm> PlaygroundDefault {
  provider round-robin
  options { strategy [ V3 ] }
}

// https://docs.boundaryml.com/docs/snippets/clients/retry
retry_policy Constant {
  max_retries 3
  // Strategy is optional
  strategy {
    type constant_delay
    delay_ms 200
  }
}

retry_policy Exponential {
  max_retries 2
  // Strategy is optional
  strategy {
    type exponential_backoff
    delay_ms 300
    mutliplier 1.5
    max_delay_ms 10000
  }
}