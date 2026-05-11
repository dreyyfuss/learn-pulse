from dataclasses import dataclass


@dataclass(frozen=True)
class Chunk:
    text: str
    index: int


class TextChunker:
    """Sliding-window character chunker.

    all-MiniLM-L6-v2 caps at 256 tokens (~400 chars), so the default
    chunk_size stays well within that limit.
    """

    def __init__(self, chunk_size: int = 400, overlap: int = 50) -> None:
        self._chunk_size = chunk_size
        self._overlap = overlap

    def chunk(self, text: str) -> list[Chunk]:
        text = text.strip()
        if not text:
            return []
        if len(text) <= self._chunk_size:
            return [Chunk(text=text, index=0)]

        chunks: list[Chunk] = []
        start = 0
        idx = 0
        step = self._chunk_size - self._overlap

        while start < len(text):
            piece = text[start : start + self._chunk_size].strip()
            if piece:
                chunks.append(Chunk(text=piece, index=idx))
                idx += 1
            start += step

        return chunks