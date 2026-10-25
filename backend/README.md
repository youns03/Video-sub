# SubVid extractor backend

This service resolves public Facebook and YouTube page URLs into a temporary direct media URL using `yt-dlp`.

## Run locally

```bash
docker build -t subvid-extractor backend
docker run --rm -p 8080:8080 subvid-extractor
curl http://localhost:8080/health
```

The Android client calls `POST /extract` with `{ "url": "https://..." }`. The returned `media_url` is temporary and must be downloaded immediately.

Only public URLs are supported. Private, login-protected, age-restricted, or region-restricted videos return an error and are not bypassed.

## Connect the Android build

Deploy the `render.yaml` service (or the Docker image on another HTTPS host), then add its HTTPS URL as the repository Actions secret `SUBVID_EXTRACTOR_URL`. The APK workflow passes that secret to Gradle at build time. If the secret is empty, the app refuses social-page URLs instead of showing a fake video.
