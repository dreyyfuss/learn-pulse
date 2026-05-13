from pydantic import BaseModel


class ChatMessage(BaseModel):
    role: str  # "user" | "assistant"
    content: str


class ChatRequest(BaseModel):
    userId: str
    message: str
    chatHistory: list[ChatMessage] = []


class ChatResponse(BaseModel):
    reply: str
    cached: bool = False
