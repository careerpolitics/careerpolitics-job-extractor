# Jib Deployment Guide (No Dockerfile)

## Why Jib
Jib builds OCI images directly from Gradle without writing or maintaining a Dockerfile.

## 1) Configure runtime env file
```bash
cp .env.example .env
```

Edit `.env` with production values.

## 2) Build locally
```bash
IMAGE_NAME=yourdockerhub/careerpolitics-scraper IMAGE_TAG=1.0.0 ./scripts/build.sh
```

## 3) Run locally
```bash
IMAGE_NAME=yourdockerhub/careerpolitics-scraper IMAGE_TAG=1.0.0 ENV_FILE=.env ./scripts/run.sh
```

Health:
```bash
curl -fsS http://localhost:8080/actuator/health
```

Logs:
```bash
docker logs -f careerpolitics-scraper-local
```

## 4) Push to registry
```bash
docker login
IMAGE_NAME=yourdockerhub/careerpolitics-scraper IMAGE_TAG=1.0.0 ./scripts/push.sh
```

## 5) Deploy on DigitalOcean Droplet
Install Docker:
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

Create env file:
```bash
mkdir -p ~/careerpolitics
nano ~/careerpolitics/.env
```

Pull and run:
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

Verify:
```bash
docker ps
curl -fsS http://localhost:8080/actuator/health
```

## Selenium + Chrome compatibility note
Jib cannot run `apt-get` during build like a Dockerfile. For Selenium support, this project uses a Debian-based base image (`selenium/standalone-chrome`) that already includes Google Chrome and required system libraries.
