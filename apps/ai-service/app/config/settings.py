from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_group_id: str = "ai-service-indexer"
    kafka_topic_course_published: str = "course.published"

    cerebras_api_key: str = ""
    cerebras_model: str = "llama-3.1-8b"

    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"
    chunk_size: int = 400
    chunk_overlap: int = 50

    chroma_host: str = "localhost"
    chroma_port: int = 8000
    chroma_collection: str = "learnpulse_courses"

    redis_url: str = "redis://localhost:6379"

    service_auth_secret: str = "change-me"
    course_service_url: str = "http://localhost:8080"

    rag_top_k: int = 5
    chat_history_window: int = 10
    chat_session_ttl_seconds: int = 604800  # 7 days

    # MinIO direct access for content indexing
    minio_endpoint: str = "http://minio:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    s3_bucket: str = "learnpulse"

    # Groq Whisper transcription
    groq_api_key: str = ""
    groq_whisper_model: str = "whisper-large-v3-turbo"


settings = Settings()


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()