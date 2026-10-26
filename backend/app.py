import json
import re
import subprocess
import tempfile
from urllib.parse import urlparse

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, HttpUrl

app = FastAPI(title="SubVid extractor", version="1.1.0")

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
    host = (parsed.hostname or "").lower().rstrip(".")
    return parsed.scheme in {"http", "https"} and any(
        host == item or host.endswith("." + item) for item in ALLOWED_HOSTS
    )


def platform_for(value: str) -> str:
    host = (urlparse(value).hostname or "").lower()
    return "facebook" if host == "fb.watch" or host.endswith("facebook.com") or host.endswith(".fb.watch") else "youtube"


@app.get("/health")
def health():
    return {"ok": True, "service": "subvid-extractor", "version": app.version}


@app.post("/extract", response_model=ExtractResponse)
def extract(request: ExtractRequest):
    url = str(request.url)
    if not is_allowed_url(url):
        raise HTTPException(status_code=400, detail="Unsupported video host")

    # The page URL is resolved on the server. Never ask Android to download a social
    # page as if it were an MP4 file. Prefer MP4 and cap the selected height at 360p.
    command = [
        "yt-dlp",
        "--no-playlist",
        "--no-warnings",
        "--dump-single-json",
        "--skip-download",
        "--format", "best[height<=360][ext=mp4]/best[height<=360]/best[ext=mp4]/best",
        "--socket-timeout", "20",
        "--retries", "2",
        "--extractor-retries", "2",
        url,
    ]

    try:
        result = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=90,
            check=False,
        )
    except subprocess.TimeoutExpired:
        raise HTTPException(status_code=504, detail="Extraction timed out")
    except OSError:
        raise HTTPException(status_code=503, detail="Extractor service is not available")

    if result.returncode != 0:
        stderr = (result.stderr or "").lower()
        if any(token in stderr for token in ("private", "login required", "sign in", "not available", "members-only")):
            detail = "This video is private, login-protected, or unavailable to the extractor."
        elif any(token in stderr for token in ("unable to download webpage", "network is unreachable", "temporary failure in name resolution", "timed out")):
            detail = "The extractor could not reach the source website. Please retry later."
        else:
            detail = "Video extraction failed. The video may be unsupported, restricted, or the source site may have changed."
        raise HTTPException(status_code=502, detail=detail)

    try:
        metadata = json.loads(result.stdout)
    except json.JSONDecodeError:
        raise HTTPException(status_code=502, detail="Extractor returned invalid metadata")

    media_url = metadata.get("url")
    if not media_url or not str(media_url).startswith(("https://", "http://")):
        raise HTTPException(status_code=502, detail="No downloadable media URL was returned")

    title = re.sub(r"\s+", " ", metadata.get("title") or "Video").strip()[:120]
    try:
        duration = max(1, int(metadata.get("duration") or 1))
    except (TypeError, ValueError):
        duration = 1

    headers = metadata.get("http_headers") or {}
    return ExtractResponse(
        title=title,
        duration_seconds=duration,
        media_url=media_url,
        platform=platform_for(url),
        http_headers={str(k): str(v) for k, v in headers.items()},
    )
