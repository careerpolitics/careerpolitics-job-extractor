# CareerPolitics Trending Service

This repository now contains **only** the Article Trending workflow, with Selenium-based scraping for trend and news discovery.

## What the service does

1. Pulls live trending topics from Google Trends through Selenium.
2. Pulls supporting headlines from Google Search News through Selenium.
3. Generates a lightweight article with a template strategy by default.
4. Optionally switches to OpenRouter article generation when configured.
5. Optionally publishes the article to the CareerPolitics article API.
6. Stores trend execution history to avoid repeating the same topic inside a cooldown window.

## Architecture

- `api`: HTTP endpoints and exception handling.
- `application`: workflow orchestration, scheduling, trend normalization, and cooldown selection.
- `domain`: request/response contracts, models, and ports.
- `infrastructure/selenium`: Selenium browser client plus trend/news adapters.
- `infrastructure/article`: article generation strategies and factory.
- `infrastructure/publisher`: outbound publishing adapter.
- `infrastructure/persistence`: JPA history storage.

## Why the Selenium version is still safer now

The original browser-driven implementation was CPU-heavy because it used large service classes and aggressive interaction logic. The current version keeps Selenium, but isolates it behind adapters, defaults to headless mode, bounds retries, reduces interaction steps, disables manual verification waits by default, and preserves scheduler overlap protection.

## Local run

```bash
./gradlew bootRun
```

## Manual Selenium verification on a local Docker browser

If Google presents a bot-check while you run the browser with `SELENIUM_HEADLESS=false`, expose Selenium's noVNC port and open:

```
http://localhost:7900/?autoconnect=1&resize=scale
```

Recommended local settings:

```
SELENIUM_HEADLESS=false
SELENIUM_MANUAL_VERIFICATION_WAIT_ENABLED=true
SELENIUM_MANUAL_VERIFICATION_MAX_WAIT_SECONDS=120
SELENIUM_REMOTE_URL=http://localhost:4444/wd/hub
```

Leave `SELENIUM_USER_AGENT` unset unless you intentionally need to spoof a specific browser version.

## Tests

```bash
./gradlew test
```

## Main endpoints

- `GET /api/trending/trends`
- `GET /api/trending/news?trend=...`
- `POST /api/trending/articles`

## Swagger UI

- Open `http://localhost:8080/swagger-ui/index.html` to browse and execute requests from the browser.
- `/`, `/docs`, and `/swagger` redirect to Swagger UI for convenience.

## Recommended production settings

- Keep `TRENDING_SCHEDULER_ENABLED=false` unless you need automated runs.
- Keep `SELENIUM_HEADLESS=true` for servers.
- Keep `SELENIUM_MAX_ATTEMPTS` low (`1` or `2`).
- Keep container limits similar to `docker-compose.yaml`.
