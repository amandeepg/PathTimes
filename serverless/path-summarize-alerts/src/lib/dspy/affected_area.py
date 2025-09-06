import dspy  # pyright: ignore[reportMissingTypeStubs]

from lib.dspy.llms import LLM

from ..models import AffectedLines, AffectedStations

ORIGINAL_PROMPT = "Analyze which stations or lines are affected by this PATH alert."


class AffectedAreaSignature(dspy.Signature):
    """
    Your input fields are:
    1. `query` (str): The alert to figure out what lines and stations are affected.
    Your output fields are:
    1. `affected_lines` (UnionType[AffectedLines, NoneType]): A list of specific lines the alerts affects
    2. `affected_stations` (UnionType[AffectedStations, NoneType]): A list of specific stations the alerts affects
    All interactions will be structured in the following way, with the appropriate values filled in.

    [[ ## query ## ]]
    {query}

    [[ ## affected_lines ## ]]
    {affected_lines}        # note: the value you produce must adhere to the JSON schema: {"$defs": {"AffectedLines": {"type": "object", "properties": {"affected_lines": {"type": "array", "items": {"$ref": "#/$defs/PathLine"}, "title": "Affected Lines"}}, "required": ["affected_lines"], "title": "AffectedLines"}, "PathLine": {"type": "string", "description": "Enum representing PATH lines.", "enum": ["NWK_WTC", "JSQ_WTC", "HOB_WTC", "JSQ_33", "HOB_33", "JSQ_33_HOB"], "title": "PathLine"}}, "anyOf": [{"$ref": "#/$defs/AffectedLines"}, {"type": "null"}]}

    [[ ## affected_stations ## ]]
    {affected_stations}        # note: the value you produce must adhere to the JSON schema: {"$defs": {"AffectedStations": {"type": "object", "properties": {"affected_stations": {"type": "array", "items": {"$ref": "#/$defs/PathStation"}, "title": "Affected Stations"}}, "required": ["affected_stations"], "title": "AffectedStations"}, "PathStation": {"type": "string", "description": "Enum representing PATH stations.", "enum": ["Newark Penn Station", "Harrison", "Journal Square", "Grove Street", "Exchange Place", "World Trade Center", "Hoboken", "Newport", "Christopher Street", "9th Street", "14th Street", "23rd Street", "33rd Street"], "title": "PathStation"}}, "anyOf": [{"$ref": "#/$defs/AffectedStations"}, {"type": "null"}]}

    [[ ## completed ## ]]
    In adhering to this structure, your objective is:
            Your task is to analyze a public transportation alert from the PATH (Port Authority Trans-Hudson) system. Based on the alert text, you must identify which specific stations and/or which specific train lines are affected by the *primary change being announced in the alert*.

            Your output must consist of two distinct lists:
            1.  `affected_stations`: A list of all stations impacted by the alert.
            2.  `affected_lines`: A list of all lines impacted by the alert.

            **CRITICAL INSTRUCTION: HIERARCHY OF ANALYSIS**

            You must follow this specific order of analysis. The distinction between a line-level and station-level issue is paramount.

            **Step 0: Identify the Primary Subject of the Alert FIRST.**
            Before all other steps, determine the core reason for the alert. Is it announcing a *new, temporary change* (like weekend service adjustments, a signal problem), or is it a general service summary that includes reminders of long-term, ongoing conditions?

            *   **Rule:** Your analysis must focus ONLY on the primary, active change being announced in the alert. Ignore information about long-term, pre-existing conditions mentioned only for rider context or as a reminder.
            *   **Example:** An alert for "Sunday Daytime Service" that also mentions "JSQ-bound 33-JSQ trains stop at Exch Pl through Jan 2026" is primarily about the Sunday service frequency/schedule changes. The Exchange Place stop is a multi-year, pre-existing condition mentioned as a reminder and must be **ignored**.

            **Step 1: Identify Line-Level Impacts.**
            A line is affected if the alert describes any change to the train service's overall pattern, route, or schedule. This is the highest-priority impact.

            Line-level impacts include:
            *   **Service Suspension:** A line is not running (e.g., "NWK-WTC service is suspended").
            *   **Delays:** Delays affecting an entire line (e.g., "Expect 20 minute delays on the HOB-33 line," "overnight trains may leave up to 10 minutes late").
            *   **Frequency/Schedule Changes:** Service running more or less frequently, on a different schedule, or with extra trains (e.g., "running 10-minute service," "extra HOB-33 every 10 mins," "JSQ-33 service will run 24/7").
            *   **Rerouting:** A line's path is altered. This includes both **bypassing** stations it normally serves and **adding stops** at stations it normally does not serve.

            **Precedence Rule: Line-Level Impact Supersedes Station-Level Impact**
            If a physical problem at a station (e.g., "switch repairs at Hoboken") or a change involving a station (e.g., "trains now stop at Exchange Place") directly results in a line-level impact (e.g., "10-minute service on HOB-WTC" or "JSQ-33 is rerouted"), the event **must** be classified as a **line-level issue**.

            In this scenario, you must list **ONLY** the affected line(s). Do **NOT** list the station where the physical problem is located or the station that is part of the reroute description. The logic is that the primary impact is to the *service*, which affects all riders of that line, not just those at one station.

            **The ONLY Exception:** If an alert explicitly states a station is **"Closed"** or being **"bypassed"**, you must list **BOTH** the affected station **AND** any lines that are consequently suspended or altered. This is the only scenario where you will list both affected stations and affected lines for the same root cause.

            **Step 2: Identify Station-Level Impacts ONLY if NO Line-Level Impact is found.**
            If, and only if, the alert's primary subject describes an issue that has **NO** impact on the overall train schedule, route, or service pattern, should you classify it as a station-level issue.

            Station-level impacts include:
            *   Elevator or escalator outages.
            *   Platform closures or changes where service continues to operate (e.g., "All service at Hoboken will operate from platforms 2 and 3").
            *   Track changes *within* a station where service continues (e.g., "All service at Newport temporarily runs from JSQ-bound track," "trains from Newark... are once again departing from their normal tracks").
            *   Temporary closures of station entrances or exits.

            **SPECIFIC RULES & DOMAIN KNOWLEDGE**

            *   **Overnight Service:** The phrases "overnight service" or "overnight trains" refer specifically to the two PATH lines that run 24/7: `NWK-WTC` and `JSQ-33 (via Hoboken)`. An alert mentioning a generic "overnight" change (e.g., delays) affects both of these lines.
            *   **Alerts Announcing Resolutions:** If an alert states that a problem has been resolved or a service is restored (e.g., "service has resumed," "elevator back in service"), you must still identify the station or line that was the subject of the alert. The entity is considered "affected" because the alert is providing a status update about it.
            *   **Transfers & Alternatives:** Do NOT list stations or lines mentioned *only* as part of an alternative solution. This includes transfer points (e.g., "transfer at Newport for a shuttle") or alternative transportation (e.g., "NJT is cross-honoring at Hoboken").
            *   **Implicit Impacts:** If a station is closed (e.g., `Hoboken`), all lines that originate/terminate there (`HOB-WTC`, `HOB-33`) or are defined by serving it (`JSQ-33 (via HOB)`) are implicitly affected (suspended) and must be listed.
            *   **Line Definitions:**
                *   `JSQ-33`: The direct weekday service between Journal Square and 33rd Street.
                *   `JSQ-33 (via Hoboken)`: The weekend and late-night service that detours to serve Hoboken. It covers the route between Journal Square and 33rd Street, with an additional stop at Hoboken.

            **Valid Station and Line Identifiers:**

            You must use the following specific identifiers when populating the lists.

            **Stations:**
            *   `Newark`: `<PathStation.NWK: 'Newark'>`
            *   `Harrison`: `<PathStation.HAR: 'Harrison'>`
            *   `Journal Square` or `JSQ`: `<PathStation.JSQ: 'Journal Square'>`
            *   `Grove Street`: `<PathStation.GRV: 'Grove Street'>`
            *   `Exchange Place`: `<PathStation.EXP: 'Exchange Place'>`
            *   `World Trade Center` or `WTC`: `<PathStation.WTC: 'World Trade Center'>`
            *   `Newport`: `<PathStation.NEW: 'Newport'>`
            *   `Hoboken`: `<PathStation.HOB: 'Hoboken'>`
            *   `Christopher Street`: `<PathStation.CHR: 'Christopher Street'>`
            *   `9th Street`: `<PathStation.9TH: '9th Street'>`
            *   `14th Street`: `<PathStation.14TH: '14th Street'>`
            *   `23rd Street`: `<PathStation.23RD: '23rd Street'>`
            *   `33rd Street`: `<PathStation.33RD: '33rd Street'>`

            **Lines:**
            *   `NWK-WTC`: `<PathLine.NWK_WTC: 'NWK_WTC'>`
            *   `HOB-WTC`: `<PathLine.HOB_WTC: 'HOB_WTC'>`
            *   `JSQ-33`: `<PathLine.JSQ_33: 'JSQ_33'>`
            *   `HOB-33`: `<PathLine.HOB_33: 'HOB_33'>`
            *   `JSQ-33 (via Hoboken)`: `<PathLine.JSQ_33_HOB: 'JSQ_33_HOB'>`

            **Examples of Correct Analysis:**

            *   **Input:** "Weekdays through 11:59pm Thursday 8/28 due to ongoing switch repairs at Hoboken, PATH will also continue running 10-minute service on the HOB-WTC and HOB-33rd St. lines during rush hours. NJT Hudson-Bergen Light Rail cross-honoring PATH customers at Exchange Place."
                *   **CORRECT Analysis:** The physical issue is at Hoboken, but the impact is a change in service frequency ("10-minute service") on two lines. This is a line-level impact that supersedes the station issue. Exchange Place is an alternative and is not affected.
                *   **CORRECT Output:** `affected_stations=[]`, `affected_lines=[<PathLine.HOB_WTC: 'HOB_WTC'>, <PathLine.HOB_33: 'HOB_33'>]`

            *   **Input:** "JSQ-bound 33-JSQ trains will now also stop at Exchange Place."
                *   **CORRECT Analysis:** A line adding a stop is a route change, making it a line-level impact. Per the Precedence Rule, only the line is listed. Exchange Place is part of the description but is not "closed" or "bypassed," so it is not listed.
                *   **CORRECT Output:** `affected_stations=[]`, `affected_lines=[<PathLine.JSQ_33: 'JSQ_33'>]`

            *   **Input:** "From 2:30-8:30am Sun 8/24, overnight svc btwn JSQ & 33 will not stop at Hoboken. Passengers traveling to/from HOB will transfer at Newport for a shuttle train."
                *   **CORRECT Analysis:** The "overnight svc btwn JSQ & 33" that serves Hoboken is the `JSQ-33 (via Hoboken)` line. The line is **bypassing** Hoboken. Per the exception rule for bypassing, both the station and the line are listed. Newport is a transfer point and is not affected.
                *   **CORRECT Output:** `affected_stations=[<PathStation.HOB: 'Hoboken'>]`, `affected_lines=[<PathLine.JSQ_33_HOB: 'JSQ_33_HOB'>]`

            *   **Input:** "Hoboken Station - Platform 1 Closure... All service at Hoboken will operate from platforms 2 and 3."
                *   **CORRECT Analysis:** A platform closure where service continues is a station-level impact. No line's schedule, route, or frequency is altered.
                *   **CORRECT Output:** `affected_stations=[<PathStation.HOB: 'Hoboken'>]`, `affected_lines=[]`

            *   **Input:** "Overnight trains may leave up to 10 minutes late for construction, maintenance, police inspections, or other operational issues."
                *   **CORRECT Analysis:** "Up to 10 minutes late" is a line-level delay. "Overnight trains" specifically refers to the `NWK-WTC` and `JSQ-33 (via Hoboken)` lines. This is a line-level issue, so no stations are listed.
                *   **CORRECT Output:** `affected_stations=[]`, `affected_lines=[<PathLine.NWK_WTC: 'NWK_WTC'>, <PathLine.JSQ_33_HOB: 'JSQ_33_HOB'>]`
    """

    query: str = dspy.InputField(  # pyright: ignore[reportUnknownMemberType]
        desc="The alert to figure out what lines and stations are affected."
    )
    affected_lines: AffectedLines | None = dspy.OutputField(  # pyright: ignore[reportUnknownMemberType]
        desc="A list of specific lines the alerts affects"
    )
    affected_stations: AffectedStations | None = dspy.OutputField(  # pyright: ignore[reportUnknownMemberType]
        desc="A list of specific stations the alerts affects"
    )


class AffectedAreaPredictor(dspy.Module):
    def __init__(self):
        super().__init__()  # pyright: ignore[reportUnknownMemberType]
        self.affected_area = dspy.Predict(AffectedAreaSignature)

    def forward(self, query: str):
        affected_area = self.affected_area(query=query)
        return dspy.Prediction(
            affected_stations=affected_area.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
            affected_lines=affected_area.affected_lines,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        )


class AffectedAreaDeterminer:
    def __init__(self):
        self.predictor = AffectedAreaPredictor()

    def determine_affected_area(
        self, query: str
    ) -> tuple[AffectedLines | None, AffectedStations | None]:
        """
        Predict affected lines and stations for a given query.

        Args:
            query: The alert text to analyze

        Returns:
            A tuple containing (affected_lines, affected_stations)
        """
        with dspy.context(lm=LLM.GEMINI_FLASH_LITE.lm):  # pyright: ignore[reportUnknownMemberType]
            prediction = self.predictor(query)
        return (prediction.affected_lines, prediction.affected_stations)  # pyright: ignore[reportUnknownVariableType, reportUnknownMemberType]
