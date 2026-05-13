import logging

import boto3
from botocore.config import Config

from app.config.settings import settings

logger = logging.getLogger(__name__)


class ContentFetcher:
    def __init__(self) -> None:
        self._s3 = boto3.client(
            "s3",
            endpoint_url=settings.minio_endpoint,
            aws_access_key_id=settings.minio_access_key,
            aws_secret_access_key=settings.minio_secret_key,
            region_name="us-east-1",
            config=Config(signature_version="s3v4"),
        )

    def fetch(self, key: str) -> bytes:
        logger.info("Fetching from MinIO bucket=%s key=%s", settings.s3_bucket, key)
        response = self._s3.get_object(Bucket=settings.s3_bucket, Key=key)
        data = response["Body"].read()
        logger.info("Fetched %d bytes for key=%s", len(data), key)
        return data
