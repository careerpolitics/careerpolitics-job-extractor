# Docker Deployment Guide (Local build -> Registry -> DigitalOcean)

## 1) Final Dockerfile
Use the repository `Dockerfile` (Debian/Ubuntu Java runtime, Google Chrome, Selenium-ready, healthcheck).

## 2) Sample `.env`
```bash
cp .env.example .env
```

Edit `.env` and set real secrets (never commit `.env`).

## 3) Local build and test
Build image locally (tags: `VERSION` and `latest`):
```bash
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 ./scripts/build.sh
```

Run locally with env file:
```bash
docker run --rm --name careerpolitics-local \
  --env-file .env \
  -p 8080:8080 \
  yourdockerhub/careerpolitics-scraper:1.0.0
```

Verify health:
```bash
curl -fsS http://localhost:8080/actuator/health
```

Read logs:
```bash
docker logs -f careerpolitics-local
```

## 4) Push image to Docker Hub
Login:
```bash
docker login
```

Push both tags:
```bash
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 ./scripts/push.sh
```

Equivalent manual commands:
```bash
docker push yourdockerhub/careerpolitics-scraper:1.0.0
docker push yourdockerhub/careerpolitics-scraper:latest
```

## 5) Deploy on DigitalOcean Droplet
### Install Docker (once)
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

### Prepare env file
```bash
mkdir -p ~/careerpolitics && cd ~/careerpolitics
nano .env
```

### Pull and run container
```bash
docker pull yourdockerhub/careerpolitics-scraper:1.0.0

docker rm -f careerpolitics-scraper || true

docker run -d \
  --name careerpolitics-scraper \
  --restart unless-stopped \
  --env-file ~/careerpolitics/.env \
  -p 8080:8080 \
  yourdockerhub/careerpolitics-scraper:1.0.0
```

### Verify deployment
```bash
docker ps
curl -fsS http://localhost:8080/actuator/health
```

## 6) Automation scripts
- `scripts/build.sh`: local build + tag (`VERSION`, `latest`)
- `scripts/push.sh`: push `VERSION` and `latest`
- `scripts/deploy.sh`: pull + recreate container with restart policy

Server usage example:
```bash
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 ENV_FILE=~/careerpolitics/.env APP_PORT=8080 ./scripts/deploy.sh
```

## 7) Optional docker-compose
```bash
cp .env.example .env
IMAGE_NAME=yourdockerhub/careerpolitics-scraper VERSION=1.0.0 docker compose up -d
```

## 8) Troubleshooting
- Check logs: `docker logs -f careerpolitics-scraper`
- Check health: `docker inspect --format='{{json .State.Health}}' careerpolitics-scraper | jq`
- Port conflict: use `-p 8081:8080`
- Missing env vars: inspect `.env` and app startup logs
