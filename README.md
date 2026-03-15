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

A new endpoint discovers Google Trends topics in India (jobs/education), researches each trend via Google News RSS from multiple sources, enriches context with media (photos/videos from source links + YouTube/social links), auto-selects a cover image when available, and generates a detailed Claude via OpenRouter markdown article for **each** trend topic. Optionally, each generated article is published to the CareerPolitics article API.

### Endpoint
`POST /api/careerpolitics/content/trends/article`

All APIs are documented in Swagger/OpenAPI (including this endpoint):
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

Example request:
```json
{
  "geo": "IN",
  "language": "en-US",
  "maxTrends": 5,
  "maxNewsPerTrend": 3,
  "publish": true,
  "fallbackTrends": ["UPSC", "NEET", "Campus Placements"]
}
```

### Required environment variables
- `OPENROUTER_API_KEY` for Claude via OpenRouter article generation
- `CAREERPOLITICS_ARTICLE_API_URL` for article publishing endpoint
- `CAREERPOLITICS_ARTICLE_API_TOKEN` optional auth token sent as `api-key`
- Optional: `careerpolitics.content.youtube-rss-url` for YouTube media discovery

### Notes
- Google Trends/Google Search can block automated scraping; if trend scraping fails, provide `fallbackTrends`.
- If `publish` is false, the API only returns generated content without posting.
- Google News RSS links are resolved to original publisher URLs before snippet/media extraction.
