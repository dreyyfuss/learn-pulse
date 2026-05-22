import pytest
from unittest.mock import MagicMock, patch

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.rag.pipeline import RagPipeline


@pytest.fixture
def mock_embedder():
    embedder = MagicMock()
    embedder.embed.return_value = [[0.1, 0.2, 0.3]]
    return embedder


@pytest.fixture
def mock_collection():
    collection = MagicMock()
    collection.query.return_value = {"documents": [["Python is great", "FastAPI rocks"]]}
    return collection


@pytest.fixture
def pipeline(mock_embedder, mock_collection):
    with patch("app.rag.pipeline.get_chroma_client"), \
         patch("app.rag.pipeline.get_collection", return_value=mock_collection):
        p = RagPipeline(embedder=mock_embedder)
    return p


def make_llm_mock(*token_strings: str) -> MagicMock:
    """Mock LLM whose astream() yields chunks with the given content strings."""
    mock_llm = MagicMock()

    async def fake_astream(messages):
        for content in token_strings:
            chunk = MagicMock()
            chunk.content = content
            yield chunk

    mock_llm.astream = fake_astream
    return mock_llm


class TestRagPipelineRetrieve:
    def test_embeds_the_query(self, pipeline, mock_embedder):
        pipeline._retrieve("what is a variable?", "course-1")
        mock_embedder.embed.assert_called_once_with(["what is a variable?"])

    def test_queries_chroma_with_embedded_vector(self, pipeline, mock_embedder, mock_collection):
        mock_embedder.embed.return_value = [[0.9, 0.8, 0.7]]
        pipeline._retrieve("q", "course-1")
        call_kwargs = mock_collection.query.call_args.kwargs
        assert call_kwargs["query_embeddings"] == [[0.9, 0.8, 0.7]]

    def test_filters_by_course_id(self, pipeline, mock_collection):
        pipeline._retrieve("q", "course-xyz")
        call_kwargs = mock_collection.query.call_args.kwargs
        assert call_kwargs["where"] == {"courseId": "course-xyz"}

    def test_respects_rag_top_k_setting(self, pipeline, mock_collection):
        with patch("app.rag.pipeline.settings") as mock_settings:
            mock_settings.rag_top_k = 3
            pipeline._retrieve("q", "c")
        call_kwargs = mock_collection.query.call_args.kwargs
        assert call_kwargs["n_results"] == 3

    def test_returns_documents_list(self, pipeline, mock_collection):
        mock_collection.query.return_value = {"documents": [["doc A", "doc B"]]}
        result = pipeline._retrieve("q", "c")
        assert result == ["doc A", "doc B"]

    def test_returns_empty_list_when_no_documents(self, pipeline, mock_collection):
        mock_collection.query.return_value = {"documents": [[]]}
        result = pipeline._retrieve("q", "c")
        assert result == []


class TestRagPipelineLlmInstance:
    def test_raises_without_api_key(self, pipeline):
        pipeline._llm = None
        with patch("app.rag.pipeline.settings") as mock_settings:
            mock_settings.groq_api_key = ""
            with pytest.raises(RuntimeError, match="GROQ_API_KEY"):
                pipeline._llm_instance()

    def test_returns_cached_llm_on_second_call(self, pipeline):
        sentinel = MagicMock()
        pipeline._llm = sentinel
        assert pipeline._llm_instance() is sentinel


class TestRagPipelineStream:
    async def test_yields_tokens_from_llm(self, pipeline):
        pipeline._llm = make_llm_mock("Hello", " world", "!")
        tokens = [t async for t in pipeline.stream("q", "course-1", "My Course", [])]
        assert tokens == ["Hello", " world", "!"]

    async def test_skips_empty_content_chunks(self, pipeline):
        pipeline._llm = make_llm_mock("Hello", "", " world", "")
        tokens = [t async for t in pipeline.stream("q", "c", "Course", [])]
        assert tokens == ["Hello", " world"]

    async def test_retrieved_context_is_in_system_prompt(self, pipeline, mock_collection):
        mock_collection.query.return_value = {"documents": [["Python loops", "List comprehensions"]]}
        captured: list = []

        async def capture_astream(messages):
            captured.extend(messages)
            yield MagicMock(content="ok")

        pipeline._llm = MagicMock()
        pipeline._llm.astream = capture_astream

        async for _ in pipeline.stream("q", "c", "Python Course", []):
            pass

        system_content = captured[0].content
        assert "Python loops" in system_content
        assert "List comprehensions" in system_content

    async def test_fallback_context_when_no_documents(self, pipeline, mock_collection):
        mock_collection.query.return_value = {"documents": [[]]}
        captured: list = []

        async def capture_astream(messages):
            captured.extend(messages)
            yield MagicMock(content="ok")

        pipeline._llm = MagicMock()
        pipeline._llm.astream = capture_astream

        async for _ in pipeline.stream("q", "c", "Course", []):
            pass

        assert "No relevant content found." in captured[0].content

    async def test_course_title_is_in_system_prompt(self, pipeline):
        captured: list = []

        async def capture_astream(messages):
            captured.extend(messages)
            yield MagicMock(content="ok")

        pipeline._llm = MagicMock()
        pipeline._llm.astream = capture_astream

        async for _ in pipeline.stream("q", "c", "Intro to Rust", []):
            pass

        assert "Intro to Rust" in captured[0].content

    async def test_history_is_added_as_langchain_messages(self, pipeline):
        captured: list = []

        async def capture_astream(messages):
            captured.extend(messages)
            yield MagicMock(content="ok")

        pipeline._llm = MagicMock()
        pipeline._llm.astream = capture_astream

        history = [
            {"role": "user", "content": "first question"},
            {"role": "assistant", "content": "first answer"},
        ]

        async for _ in pipeline.stream("new question", "c", "Course", history):
            pass

        # system + user-history + assistant-history + current query = 4
        assert len(captured) == 4
        assert isinstance(captured[0], SystemMessage)
        assert isinstance(captured[1], HumanMessage)
        assert captured[1].content == "first question"
        assert isinstance(captured[2], AIMessage)
        assert captured[2].content == "first answer"
        assert isinstance(captured[3], HumanMessage)
        assert captured[3].content == "new question"

    async def test_unknown_history_roles_are_ignored(self, pipeline):
        captured: list = []

        async def capture_astream(messages):
            captured.extend(messages)
            yield MagicMock(content="ok")

        pipeline._llm = MagicMock()
        pipeline._llm.astream = capture_astream

        history = [{"role": "system", "content": "ignore me"}]
        async for _ in pipeline.stream("q", "c", "Course", history):
            pass

        # Only system prompt + current query (unknown role dropped)
        assert len(captured) == 2

    async def test_empty_history_produces_system_and_query_only(self, pipeline):
        captured: list = []

        async def capture_astream(messages):
            captured.extend(messages)
            yield MagicMock(content="ok")

        pipeline._llm = MagicMock()
        pipeline._llm.astream = capture_astream

        async for _ in pipeline.stream("q", "c", "Course", []):
            pass

        assert len(captured) == 2
        assert isinstance(captured[0], SystemMessage)
        assert isinstance(captured[1], HumanMessage)
