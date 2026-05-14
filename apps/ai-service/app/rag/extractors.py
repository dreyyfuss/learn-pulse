import re
from io import BytesIO
from pathlib import Path


def extract_text(content_bytes: bytes, content_key: str) -> str:
    ext = Path(content_key).suffix.lower()
    if ext == ".md":
        return _strip_markdown(content_bytes.decode("utf-8", errors="ignore"))
    if ext == ".pdf":
        return _extract_pdf(content_bytes)
    if ext == ".docx":
        return _extract_docx(content_bytes)
    return content_bytes.decode("utf-8", errors="ignore")


def _strip_markdown(text: str) -> str:
    # Remove fenced code blocks
    text = re.sub(r"```.*?```", "", text, flags=re.DOTALL)
    text = re.sub(r"`[^`]+`", "", text)
    # Remove headings markers
    text = re.sub(r"^#{1,6}\s+", "", text, flags=re.MULTILINE)
    # Remove bold/italic
    text = re.sub(r"\*{1,3}(.+?)\*{1,3}", r"\1", text)
    text = re.sub(r"_{1,3}(.+?)_{1,3}", r"\1", text)
    # Remove links — keep link text
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    # Remove images
    text = re.sub(r"!\[[^\]]*\]\([^)]+\)", "", text)
    # Remove horizontal rules
    text = re.sub(r"^[-*_]{3,}\s*$", "", text, flags=re.MULTILINE)
    # Remove HTML tags
    text = re.sub(r"<[^>]+>", "", text)
    # Collapse excessive blank lines
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def _extract_pdf(content_bytes: bytes) -> str:
    import pdfplumber

    pages: list[str] = []
    with pdfplumber.open(BytesIO(content_bytes)) as pdf:
        for page in pdf.pages:
            page_text = page.extract_text()
            if page_text:
                pages.append(page_text)
    return "\n\n".join(pages)


def _extract_docx(content_bytes: bytes) -> str:
    from docx import Document

    doc = Document(BytesIO(content_bytes))
    return "\n".join(p.text for p in doc.paragraphs if p.text.strip())
