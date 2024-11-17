from pydantic import BaseModel, Field

class AlertSummary(BaseModel):
    text: str
    is_delay: bool = Field(description="indicating if the alert is about a delay on lines, true is yes, false if it is a general announcement")
    is_relevant: bool = Field(description="indicating if the alert affects the rider's experience or not")