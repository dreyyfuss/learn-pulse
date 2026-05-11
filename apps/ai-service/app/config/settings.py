from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_group_id: str = "ai-service-indexer"
    kafka_topic_course_published: str = "course.published"

    cerebras_api_key: str = ""
    cerebras_model: str = "llama-3.3-70b"

    # Local model used for embeddings.
    # Cerebras does not expose an embeddings API, so sentence-transformers
    # is always used; swap the model name here to try a different local model.
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"
    chunk_size: int = 400
    chunk_overlap: int = 50

    chroma_host: str = "localhost"
    chroma_port: int = 8000
    chroma_collection: str = "learnpulse_courses"


settings = Settings()