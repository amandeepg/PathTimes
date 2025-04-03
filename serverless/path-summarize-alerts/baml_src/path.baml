template_string ChainOfThought() #"
    Outline some relevant information before you answer.
    Example:
    - ...
    - ...
    ...
    {
      ... // schema
    }
"#

template_string ChainOfThoughtIf() #"
    {%if ctx.client.name == "deepseek/deepseek-r1:price" %}
    {%elif ctx.client.name == "deepseek/deepseek-r1" %}
    {%elif ctx.client.name == "anthropic/claude-3.7-sonnet:thinking" %}
    {%elif ctx.client.name == "anthropic/claude-3.7-sonnet" %}
    {%elif ctx.client.name == "openai/o3-mini-high" %}
    {% else %}
      {{ ChainOfThought() }}
    {% endif %}
"#

enum PathStation {
  NWK @description("Newark Penn Station")
  HAR @description("Harrison")
  JSQ @description("Journal Square")
  GRV @description("Grove Street")
  EXP @description("Exchange Place")
  WTC @description("World Trade Center")
  HOB @description("Hoboken")
  NEW @description("Newport")
  CHR @description("Christopher Street")
  S09 @description("9th Street")
  S14 @description("14th Street")
  S23 @description("23rd Street")
  S33 @description("33rd Street")
}

enum PathLine {
  NWK_WTC @description("Newark - World Trade Center")
  JSQ_WTC @description("Journal Square - World Trade Center")
  HOB_WTC @description("Hoboken - World Trade Center")
  JSQ_33 @description("Journal Square - 33rd Street")
  HOB_33 @description("Hoboken - 33rd Street")
  JSQ_33_HOB @description("Journal Square - 33rd Street (via Hoboken)")
}

class AffectedStations {
  affected_stations PathStation[]
}

class AffectedRoutes {
  affected_routes PathLine[]
}

class IsDelay {
  is_delay bool
}

class AlertSummary {
  alert_summary string
}

function GetAlertSummary(alert: string) -> AlertSummary {
  client PlaygroundDefault
  prompt #"
    You are a very helpful transit agency alert summarizer to take alert text from a transit agency (the PATH transit system in NYC and NJ) and make it more digestible for transit riders.
    Shorten the text by omitting unnecessary fluff and politeness, assuming the rider is an experienced traveler, they don't need to be coddled by telling them to pay attention or that to leave extra time for delays.
    Riders already know that delays do cause inconvenience and that it will slow their journey.
    References to PATH should be removed if implied, keeping in mind that the target audience is familiar with PATH and utilizes an app exclusive to this transit line.
    Represent dates using American formats with month names, like "Sunday January 26th" or "Monday December 2nd". 
    If no day of the week is specified, then simply use a format like "January 26th".
    Remove the word "tips" but just give the advice from the tips succinctly.
    Events at "Sports Illustrated Stadium" are Red Bulls soccer games. Refer to the stadium as "Sports Illustrated Stadium (formerly Red Bull Area) in Harrison".
    Lines are formatted like "JSQ-33" or "NWK-WTC" or "JSQ-33 via HOB".
    If the alert concerns a single station, you must omit mentioning the station name.
    Remove any generic information text about issuing an update later.
    Remove any links or URLs.
    Make sure to keep information about cross-honoring.
    Your answer is very important for my job and life! So think carefully and take your time!! Don't make mistakes!

    This is your job: Summarize this PATH train alert concisely while preserving all important information.
    .

    {{ ctx.output_format }}
    .
    {{ ChainOfThoughtIf() }}
    .

    {{ _.role("user") }} {{ alert }}
  "#
}

function IsDelayAlert(alert: string) -> IsDelay {
  client PlaygroundDefault
  prompt #"
    Determine if this PATH train alert is about a service delay.
    Return true only if the alert mentions delays or service disruptions that cause delays for people on the train or waiting for the train.
    Trains skipping stops or closed stations are not delays.
    A delay means that the train will be running slower or arrive later than the rider anticipates.
    .

    {{ ctx.output_format }}
    .
    {{ ChainOfThoughtIf() }}
    .

    {{ _.role("user") }} {{ alert }}
  "#
}

function GetAffectedArea(alert: string) -> (AffectedStations | AffectedRoutes)? {
  client PlaygroundDefault
  prompt #"
    Analyze which stations or lines are affected by this PATH alert.
    Return either a list of specific stations OR a list of affected lines, but not both.
    Choose based on which is more specifically impacted by the alert.
    .

    {{ ctx.output_format }}
    .
    {{ ChainOfThoughtIf() }}
    .

    {{ _.role("user") }} {{ alert }}
  "#
}

test SummaryTest {
  functions [GetAlertSummary]
  args {
    alert "Due to signal problems at Journal Square, JSQ-33rd Street via HOB service is operating with delays."
  }
}

test DelayTest {
  functions [IsDelayAlert]
  args {
    alert "Due to signal problems at Journal Square, JSQ-33rd Street via HOB service is operating with delays."
  }
  @@assert(isRight, {{ this|attr('is_delay') == true }})
}

test AffectedAreaTest {
  functions [GetAffectedArea]
  args {
    alert "Due to scheduled track work, PATH service on the Newark–World Trade Center line is suspended. New Jersey Transit and private carrier buses are cross-honoring PATH tickets and SmartLink cards."
  }
  @@assert(isRight, {{ (this|attr('affected_routes'))[0] == "NWK_WTC" }})
}

test NoAffectedAreaTest {
  functions [GetAffectedArea]
  args {
    alert "Happy Holidays from everyone at PATH! Have a great Christmas-time."
  }
  @@assert(isRight, {{ this == "" }})
}
