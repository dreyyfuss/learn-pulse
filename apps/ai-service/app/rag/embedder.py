import logging

from sentence_transformers import SentenceTransformer

from app.config.settings import settings

logger = logging.getLogger(__name__)


class Embedder:
    """Wraps a local sentence-transformers model.

    Cerebras does not provide an embeddings API, so the local
    sentence-transformers model is always used. The model name is
    controlled by settings.embedding_model so it can be swapped without
    code changes.
    """

    def __init__(self) -> None:
        self._model: SentenceTransformer | None = None

    def load(self) -> None:
        logger.info("Loading embedding model %r", settings.embedding_model)
        self._model = SentenceTransformer(settings.embedding_model)
        dim = self._model.get_embedding_dimension()
        logger.info("Embedding model loaded dim=%d", dim)

    def embed(self, texts: list[str]) -> list[list[float]]:
        if self._model is None:
            raise RuntimeError("Embedder.load() must be called before embed()")
        vectors = self._model.encode(texts, convert_to_numpy=True)
        return vectors.tolist()