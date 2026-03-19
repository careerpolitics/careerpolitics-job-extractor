# CareerPolitics Trending Service

This repository now contains **only** the Article Trending workflow.

## What the service does

1. Pulls live trending topics from Google Trends RSS.
2. Pulls supporting headlines from Google News RSS.
3. Generates a lightweight article with a template strategy by default.
4. Optionally switches to OpenRouter article generation when configured.
5. Optionally publishes the article to the CareerPolitics article API.
6. Stores trend execution history to avoid repeating the same topic inside a cooldown window.

## Architecture

- `api`: HTTP endpoints and exception handling.
- `application`: workflow orchestration, scheduling, trend normalization, and cooldown selection.
- `domain`: request/response contracts, models, and ports.
- `infrastructure/google`: RSS clients for Google Trends and Google News.
- `infrastructure/article`: article generation strategies and factory.
- `infrastructure/publisher`: outbound publishing adapter.
- `infrastructure/persistence`: JPA history storage.

## Why CPU usage dropped

The previous implementation used Selenium with retry loops, browser automation, consent handling, and manual verification waits. That architecture was the main source of 100% CPU in Docker. The refactored service removes the browser entirely and replaces it with inexpensive RSS/HTTP calls.

## Local run

```bash
./gradlew bootRun
```

## Tests

```bash
./gradlew test
```

## Main endpoints

- `GET /api/trending/trends`
- `GET /api/trending/news?trend=...`
- `POST /api/trending/articles`

## Recommended production settings

- Keep `TRENDING_SCHEDULER_ENABLED=false` unless you need automated runs.
- Run the scheduler every 6 hours or slower.
- Keep publishing disabled unless API credentials are configured.
- Use container limits similar to `docker-compose.yaml`.
