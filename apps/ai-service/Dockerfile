FROM mwader/static-ffmpeg:7.0 AS ffmpeg

FROM python:3.12-slim
COPY --from=ffmpeg /ffmpeg /usr/local/bin/ffmpeg
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    python3-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt requirements-test.txt ./
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install torch --index-url https://download.pytorch.org/whl/cpu
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt -r requirements-test.txt
COPY app/ app/
COPY tests/ tests/
COPY pytest.ini .
EXPOSE 9000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9000"]
