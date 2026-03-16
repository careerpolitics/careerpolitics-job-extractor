# Deploy to DigitalOcean App Platform

This repository includes an App Platform spec at `.do/app.yaml`.

## 1) Prerequisites
- A DigitalOcean account
- A GitHub repo connected to DigitalOcean
- `OPENROUTER_API_KEY` and article API secrets ready

## 2) Important app runtime settings
- App listens on `PORT` (configured in Spring as `server.port=${PORT:8080}`)
- Health check endpoint: `/actuator/health`
- For App Platform, Selenium should run headless:
  - `SELENIUM_HEADLESS=true`
  - `SELENIUM_MANUAL_VERIFICATION_WAIT_ENABLED=false`

## 3) Deploy with App Platform UI
1. In DigitalOcean, go to **Apps** → **Create App**.
2. Choose your GitHub repo and branch.
3. Choose **Use existing app spec** and point to `.do/app.yaml`.
4. Fill secret values:
   - `OPENROUTER_API_KEY`
   - `CAREERPOLITICS_ARTICLE_API_URL`
   - `CAREERPOLITICS_ARTICLE_API_TOKEN`
5. Create the app and wait for deployment.

## 4) Deploy with doctl (CLI)
```bash
# Install & auth doctl first
# doctl auth init

doctl apps create --spec .do/app.yaml
```

Update existing app:
```bash
doctl apps list
# copy app id

doctl apps update <APP_ID> --spec .do/app.yaml
```

## 5) Verify deployment
- Health: `https://<your-app-domain>/actuator/health`
- Swagger UI: `https://<your-app-domain>/swagger-ui/index.html`

## 6) Optional: switch to Postgres
The spec includes a commented block to add a managed Postgres database and wire Spring datasource env vars. Uncomment and deploy if you want persistent production data.

## 7) Troubleshooting
- If startup fails on port binding, confirm `server.port=${PORT:8080}` is present.
- If Selenium fails in cloud runtime, keep `SELENIUM_HEADLESS=true` and reduce retries (`SELENIUM_MAX_ATTEMPTS=2/3`).
- If traffic/bot checks are high, prefer fallback trends and tune Selenium toggles via envs.
