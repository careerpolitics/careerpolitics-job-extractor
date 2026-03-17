# CareerPolitics Job Scraper

A Spring Boot service to discover job URLs, scrape detailed job information, and manage/export results. Includes OpenAPI/Swagger UI, H2 in-memory DB for local dev, and Docker support.

## Features
- URL discovery (e.g., sarkariexam)
- Detail scraping with basic parsing
- Data management (paging, filters, markdown generation)
- Orchestration (full cycle run, simple schedules, metrics)
- OpenAPI docs (Swagger UI)

## Tech Stack
- Java 17, Spring Boot 3
- Spring Web, Data JPA, Validation, Caching, Actuator
- H2 (local dev), PostgreSQL (runtime option)
- Jsoup, imgscalr, springdoc-openapi

## Quickstart

### Prerequisites
- Java 17+
- Bash

### Run locally
```bash
./gradlew bootRun
```
App runs at `http://localhost:8080` with API routes under `/api`.

- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:careerpoliticsdb`)

### Build JAR
```bash
./gradlew clean build -x test
```
Artifact: `build/libs/careerpolitics-job-scraper.jar`

### Docker (via Jib)
```bash
./gradlew jibDockerBuild
```
Image: `careerpolitics/scraper:latest`

Run container:
```bash
docker run --rm -p 8080:8080 careerpolitics/scraper:latest
```

## Export OpenAPI spec
Export JSON:
```bash
curl -s http://localhost:8080/v3/api-docs > openapi.json
```
Export YAML:
```bash
curl -s http://localhost:8080/v3/api-docs.yaml > openapi.yaml
```
Or use the helper script after starting the app:
```bash
bash scripts/export-openapi.sh
```

## Key Endpoints (base `/api`)
- URL Collection: `/careerpolitics/url-collection/*`
- Detail Scraping: `/careerpolitics/detail-scraping/*`
- Data Management: `/careerpolitics/data/*`
- Orchestration: `/careerpolitics/orchestration/*`
- Config: `/careerpolitics/config/*`

## Configuration
See `src/main/resources/application.yaml`.

Optional env vars:
- `AWS_REGION` (default `us-east-1`) for S3Client

## Notes
- Defaults seed in `data.sql` for H2
- For production, configure PostgreSQL datasource and Spring profiles
## Trending Jobs/Education Article Generation

A new endpoint discovers Google Trends topics in India (jobs/education), researches each trend via Google Trends (Selenium/page scrape) and Google Search news results from multiple sources, enriches context with media (photos/videos from source links + YouTube/social links), auto-selects a cover image when available, and generates a detailed Claude via OpenRouter markdown article for **each** trend topic. For each generated article, the API request to CareerPolitics article API is sent with `published` flag based on request (`true`/`false`, default `false`).

### Endpoint
`POST /api/careerpolitics/content/trends/article`

`GET /api/careerpolitics/content/news/rss/resolve-first?query=ssc&hl=en-US&gl=US&ceid=US:en`

All APIs are documented in Swagger/OpenAPI (including these endpoints):
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

Example request:
```json
{
  "geo": "IN",
  "language": "en-US", // set hi-IN, bn-IN, ta-IN, etc. to generate in that language
  "maxTrends": 5,
  "maxNewsPerTrend": 3,
  "trendCooldownHours": 24,
  "publish": false,
  "fallbackTrends": ["UPSC", "NEET", "Campus Placements"]
}
```

### Required environment variables
- `OPENROUTER_API_KEY` for Claude via OpenRouter article generation
- `CAREERPOLITICS_ARTICLE_API_URL` for article publishing endpoint
- `CAREERPOLITICS_ARTICLE_API_TOKEN` optional auth token sent as `api-key`
- Optional: `SELENIUM_ENABLED` (default `true`) to enable Selenium scraping
- Optional: `SELENIUM_NEWS_ENABLED` (default `true`) to enable Selenium-based Google Search news scraping
- Optional: `SELENIUM_HEADLESS` (default `false`) to open browser window for debugging when Selenium runs
- Optional: `SELENIUM_TIMEOUT_SECONDS` (default `20`) for Selenium wait timeout
- Optional: `TRENDS_SCHEDULER_ENABLED` (default `false`) to run automatic hourly `/trends/article` pipeline
- Optional: `TRENDS_SCHEDULER_CRON` (default `0 0 * * * *`) for schedule control (hourly by default)
- Optional: `TRENDS_SCHEDULER_TREND_COOLDOWN_HOURS` (default `24`) to reduce repeating the same trend
- Optional: `SELENIUM_MANUAL_VERIFICATION_WAIT_ENABLED` (default `true`) waits when Google bot-check appears so you can manually verify in browser.
- Optional: `SELENIUM_MANUAL_VERIFICATION_MAX_WAIT_SECONDS` (default `180`) maximum wait for manual verification completion.
- `language` request field also controls article output language (for example: `en-US`, `hi-IN`, `ta-IN`).
- Optional: `careerpolitics.content.youtube-rss-url` for YouTube media discovery


### Automatic hourly run (extract + post article)
When `TRENDS_SCHEDULER_ENABLED=true`, the service triggers the same trend article pipeline every hour (cron default `0 0 * * * *`). It generates and publishes posts automatically and applies trend cooldown filtering so recently used trend slugs are deprioritized for the next runs.

### Notes
- Error responses: API returns structured error JSON for workflow failures (e.g., no trends found, no news found for all trends).
- If `fallbackTrends` is provided, those values are used with higher priority than live trend scraping.
- API sends article payload to CareerPolitics endpoint with `article.published` set from `publish` (`false` by default when omitted).
- Google Search/Google News wrapped links are resolved to original publisher URLs before snippet/media extraction.


## DigitalOcean App Platform Deployment

- App spec file: `.do/app.yaml`
- Step-by-step guide: `docs/digitalocean-app-platform.md`

Quick deploy with CLI:
```bash
doctl apps create --spec .do/app.yaml
```


### App Platform Docker build note
The Dockerfile builds the JAR inside a multi-stage build, so App Platform does not require a prebuilt `build/libs/*.jar` artifact in the repo context.

## Simple Docker Production Workflow

### 1) Create runtime env file
Copy the example and fill real values:
```bash
cp .env.example .env
```

Never bake secrets into Docker image. The container reads values from `--env-file .env` at runtime.

### 2) Build image locally
```bash
./scripts/build.sh
```

Custom name/tag:
```bash
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 ./scripts/build.sh
```

### 3) Run locally with env file
```bash
docker run --rm --name careerpolitics-local \
  --env-file .env \
  -p 8080:8080 \
  yourdockerhub/careerpolitics-scraper:1.0.0
```

Check logs:
```bash
docker logs -f careerpolitics-local
```

Verify app:
```bash
curl -fsS http://localhost:8080/actuator/health
```

### 4) Push to Docker Hub / Registry
Login once:
```bash
docker login
```

Push version + latest tags:
```bash
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 ./scripts/push.sh
```

Manual equivalent:
```bash
docker push yourdockerhub/careerpolitics-scraper:1.0.0
docker push yourdockerhub/careerpolitics-scraper:latest
```

### 5) Deploy on DigitalOcean Droplet
On the server:

Install Docker:
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

Create deployment folder and env file:
```bash
mkdir -p ~/careerpolitics && cd ~/careerpolitics
nano .env
```

Pull + run image:
```bash
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 ENV_FILE=.env APP_PORT=8080 bash -c "$(curl -fsSL https://raw.githubusercontent.com/<your-org>/<your-repo>/<your-branch>/scripts/deploy.sh)"
```

Or if repository is cloned on server:
```bash
cd ~/careerpolitics-job-extractor
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 ENV_FILE=.env APP_PORT=8080 ./scripts/deploy.sh
```

### 6) Reliability and operations
- Restart policy is `unless-stopped` (auto-start after reboot).
- See running container:
```bash
docker ps
```
- Follow logs:
```bash
docker logs -f careerpolitics-scraper
```
- Restart manually:
```bash
docker restart careerpolitics-scraper
```

### Optional: docker compose
```bash
cp .env.example .env
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 docker compose up -d
```

### Troubleshooting
- **Container exits immediately**: `docker logs careerpolitics-scraper` and confirm required env vars in `.env`.
- **Health check unhealthy**: ensure app exposes `/actuator/health` and startup has completed.
- **Chrome/Selenium issues**: keep `SELENIUM_HEADLESS=true` in server env.
- **Port already in use**: change `APP_PORT` (for example `APP_PORT=8081`).
