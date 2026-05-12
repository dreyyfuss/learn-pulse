from pydantic import BaseModel


class SessionResponse(BaseModel):
    sessionId: str


class MessageRequest(BaseModel):
    message: str
