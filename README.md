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

## Honeybadger

The app supports a small Honeybadger setup for production errors and logs.

Set these environment variables if you want Honeybadger enabled:

```
HONEYBADGER_API_KEY=your_project_api_key
HONEYBADGER_ENVIRONMENT=production
HONEYBADGER_LOGGING_ENABLED=true
```

Behavior:

- Unhandled API exceptions are sent to Honeybadger as error notices.
- Application logs at `WARN` and above are forwarded to Honeybadger Events.
- Console logging still stays enabled.

## GitHub Docker deployment pipeline

A GitHub Actions workflow is included at `.github/workflows/docker-deploy.yml`.

On pushes to `main` or manual runs, it:

1. Runs `./gradlew test`.
2. Builds a Docker image from `Dockerfile`.
3. Pushes the image to GitHub Container Registry.
4. Optionally deploys to your server with `docker compose` over SSH.

To enable the deploy step, add these GitHub repository secrets:

- `DEPLOY_HOST`
- `DEPLOY_PORT` (optional)
- `DEPLOY_USER`
- `DEPLOY_SSH_KEY`
- `DEPLOY_PATH`
- `APP_ENV_FILE` (the full `.env` file content for the server)

## Local run

```bash
./gradlew bootRun
```

## Manual Selenium verification on a local Docker browser

If Google presents a bot-check while you run the browser with `SELENIUM_HEADLESS=false`, open:

```
http://localhost:7900/?autoconnect=1&resize=scale
```

By default, `docker-compose.yaml` now binds Selenium's WebDriver (`4444`) and noVNC (`7900`) ports to `127.0.0.1` only. That keeps the browser internal to the Docker host and prevents internet scanners from reaching Selenium directly on a public droplet. If you really need remote debugging from another machine, override `SELENIUM_HOST_BIND` and `SELENIUM_VNC_HOST_BIND` explicitly.

Recommended local settings:

```
SELENIUM_HEADLESS=false
SELENIUM_MANUAL_VERIFICATION_WAIT_ENABLED=true
SELENIUM_MANUAL_VERIFICATION_MAX_WAIT_SECONDS=120
SELENIUM_REMOTE_URL=http://localhost:4444/wd/hub
SELENIUM_HOST_BIND=127.0.0.1
SELENIUM_VNC_HOST_BIND=127.0.0.1
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
- If you do enable the scheduler, prefer a six-hour cadence or slower to avoid repeated Chrome spikes on small droplets.
- Keep `SELENIUM_HEADLESS=true` for servers.
- Keep `SELENIUM_MAX_ATTEMPTS` low (`1` or `2`).
- Do not publish Selenium ports (`4444`/`7900`) publicly unless you intentionally need remote debugging.
- Keep container limits similar to `docker-compose.yaml`.
