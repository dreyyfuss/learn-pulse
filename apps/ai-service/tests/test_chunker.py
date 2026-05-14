import pytest

from app.rag.chunker import Chunk, TextChunker


class TestChunkDataclass:
    def test_is_frozen(self):
        chunk = Chunk(text="hello", index=0)
        with pytest.raises((AttributeError, TypeError)):
            chunk.text = "other"  # type: ignore[misc]

    def test_equality_by_value(self):
        assert Chunk(text="hello", index=0) == Chunk(text="hello", index=0)

    def test_inequality_on_different_index(self):
        assert Chunk(text="hello", index=0) != Chunk(text="hello", index=1)


class TestTextChunker:
    def test_empty_string_returns_empty(self):
        chunker = TextChunker()
        assert chunker.chunk("") == []

    def test_whitespace_only_returns_empty(self):
        chunker = TextChunker()
        assert chunker.chunk("   \n\t  ") == []

    def test_short_text_is_single_chunk(self):
        chunker = TextChunker(chunk_size=400, overlap=50)
        result = chunker.chunk("Short text")
        assert result == [Chunk(text="Short text", index=0)]

    def test_text_exactly_chunk_size_is_single_chunk(self):
        chunker = TextChunker(chunk_size=10, overlap=2)
        result = chunker.chunk("a" * 10)
        assert len(result) == 1
        assert result[0].index == 0

    def test_long_text_produces_multiple_chunks(self):
        chunker = TextChunker(chunk_size=10, overlap=2)
        # step = 10 - 2 = 8; 30 chars → ceil((30-10)/8)+1 = 4 chunks
        result = chunker.chunk("a" * 30)
        assert len(result) > 1

    def test_indices_are_sequential_from_zero(self):
        chunker = TextChunker(chunk_size=5, overlap=1)
        result = chunker.chunk("a" * 50)
        assert [c.index for c in result] == list(range(len(result)))

    def test_overlap_produces_shared_content(self):
        chunker = TextChunker(chunk_size=10, overlap=5)
        # step = 5; text[0:10] and text[5:15] share text[5:10]
        text = "0123456789ABCDEFGHIJ"
        result = chunker.chunk(text)
        assert len(result) >= 2
        # Last 5 chars of chunk 0 == first 5 chars of chunk 1
        assert result[0].text[-5:] == result[1].text[:5]

    def test_strips_leading_and_trailing_whitespace(self):
        chunker = TextChunker()
        result = chunker.chunk("  hello world  ")
        assert result == [Chunk(text="hello world", index=0)]

    def test_chunk_text_lengths_respect_chunk_size(self):
        chunker = TextChunker(chunk_size=10, overlap=2)
        result = chunker.chunk("a" * 100)
        for chunk in result:
            assert len(chunk.text) <= 10

    def test_no_empty_chunks_in_result(self):
        chunker = TextChunker(chunk_size=5, overlap=1)
        result = chunker.chunk("a" * 50)
        for chunk in result:
            assert chunk.text != ""

    def test_single_character_text(self):
        chunker = TextChunker()
        result = chunker.chunk("x")
        assert result == [Chunk(text="x", index=0)]

    def test_unicode_text(self):
        chunker = TextChunker(chunk_size=5, overlap=1)
        result = chunker.chunk("αβγδεζηθ")
        assert len(result) >= 1
        for chunk in result:
            assert chunk.text != ""
