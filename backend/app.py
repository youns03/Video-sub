import os
import re
import subprocess
import tempfile
from urllib.parse import urlparse

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, HttpUrl

app = FastAPI(title="SubVid extractor", version="1.0.0")

ALLOWED_HOSTS = ("facebook.com", "fb.watch", "youtube.com", "youtu.be")


class ExtractRequest(BaseModel):
    url: HttpUrl


class ExtractResponse(BaseModel):
    title: str
    duration_seconds: int
    media_url: str
    platform: str
    http_headers: dict[str, str] = {}


def is_allowed_url(value: str) -> bool:
    parsed = urlparse(value)
    host = (parsed.hostname or "").lower()
    return parsed.scheme in {"http", "https"} and any(
        host == item or host.endswith("." + item) for item in ALLOWED_HOSTS
    )


def platform_for(value: str) -> str:
    host = (urlparse(value).hostname or "").lower()
    return "facebook" if "facebook.com" in host or "fb.watch" in host else "youtube"


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/extract", response_model=ExtractResponse)
def extract(request: ExtractRequest):
    url = str(request.url)
    if not is_allowed_url(url):
        raise HTTPException(status_code=400, detail="Unsupported video host")

    with tempfile.TemporaryDirectory(prefix="subvid-") as temp_dir:
        command = [
            "yt-dlp",
            "--no-playlist",
            "--no-warnings",
            "--dump-single-json",
            "--skip-download",
            "--format", "best[height<=360]/best",
            "--socket-timeout", "20",
            url,
        ]
        try:
            result = subprocess.run(
                command,
                cwd=temp_dir,
                capture_output=True,
                text=True,
                timeout=90,
                check=False,
            )
        except subprocess.TimeoutExpired:
            raise HTTPException(status_code=504, detail="Extraction timed out")

        if result.returncode != 0:
            detail = (result.stderr or "Extraction failed").strip()[-1000:]
            raise HTTPException(status_code=502, detail=detail)

        import json
        try:
            metadata = json.loads(result.stdout)
        except json.JSONDecodeError:
            raise HTTPException(status_code=502, detail="Extractor returned invalid metadata")

    media_url = metadata.get("url")
    if not media_url:
        raise HTTPException(status_code=502, detail="No downloadable media URL was returned")

    title = re.sub(r"\s+", " ", metadata.get("title") or "Video").strip()[:120]
    duration = max(1, int(metadata.get("duration") or 1))
    return ExtractResponse(
        title=title,
        duration_seconds=duration,
        media_url=media_url,
        platform=platform_for(url),
        http_headers={str(k): str(v) for k, v in (metadata.get("http_headers") or {}).items()},
    )
