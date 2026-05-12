import pytest
from unittest.mock import MagicMock, patch

from app.rag.embedder import Embedder


@pytest.fixture
def mock_model():
    model = MagicMock()
    model.get_embedding_dimension.return_value = 384
    fake_output = MagicMock()
    fake_output.tolist.return_value = [[0.1, 0.2, 0.3]]
    model.encode.return_value = fake_output
    return model


@pytest.fixture
def loaded_embedder(mock_model):
    embedder = Embedder()
    with patch("app.rag.embedder.SentenceTransformer", return_value=mock_model):
        embedder.load()
    return embedder


class TestEmbedder:
    def test_embed_before_load_raises_runtime_error(self):
        embedder = Embedder()
        with pytest.raises(RuntimeError, match="load\\(\\) must be called"):
            embedder.embed(["hello"])

    def test_load_sets_internal_model(self, mock_model):
        embedder = Embedder()
        with patch("app.rag.embedder.SentenceTransformer", return_value=mock_model) as MockST:
            embedder.load()
        assert embedder._model is mock_model

    def test_load_uses_embedding_model_from_settings(self, mock_model):
        embedder = Embedder()
        with patch("app.rag.embedder.SentenceTransformer", return_value=mock_model) as MockST, \
             patch("app.rag.embedder.settings") as mock_settings:
            mock_settings.embedding_model = "custom/model"
            embedder.load()
            MockST.assert_called_once_with("custom/model")

    def test_embed_calls_encode_with_texts(self, loaded_embedder, mock_model):
        loaded_embedder.embed(["hello", "world"])
        mock_model.encode.assert_called_once_with(["hello", "world"], convert_to_numpy=True)

    def test_embed_returns_list_of_lists(self, loaded_embedder, mock_model):
        mock_model.encode.return_value.tolist.return_value = [[0.1, 0.2], [0.3, 0.4]]
        result = loaded_embedder.embed(["hello", "world"])
        assert result == [[0.1, 0.2], [0.3, 0.4]]

    def test_embed_empty_input(self, loaded_embedder, mock_model):
        mock_model.encode.return_value.tolist.return_value = []
        result = loaded_embedder.embed([])
        assert result == []

    def test_embed_single_text(self, loaded_embedder, mock_model):
        mock_model.encode.return_value.tolist.return_value = [[0.5, 0.6, 0.7]]
        result = loaded_embedder.embed(["one sentence"])
        assert len(result) == 1
        assert result[0] == [0.5, 0.6, 0.7]
