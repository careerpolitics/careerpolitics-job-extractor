# Deploy to a DigitalOcean Droplet (using Docker Hub image)

This flow builds the image on your local machine, pushes it to Docker Hub, and deploys that image on the Droplet.

## 1) Create Droplet
- Ubuntu 22.04 LTS
- At least 2 vCPU / 4 GB RAM recommended (Selenium + Chrome are memory heavy)
- Open inbound ports:
  - `22` for SSH
  - `8080` for direct API access (or `80/443` if reverse proxy is used)

## 2) Install Docker + Compose plugin on Droplet
```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

Log out/in once so Docker group permissions apply.

## 3) Prepare env vars locally
```bash
cp .env.droplet.example .env.droplet
nano .env.droplet
```

Set at least:
- `OPENROUTER_API_KEY`
- `CAREERPOLITICS_CONTENT_ARTICLE_API_URL`
- `CAREERPOLITICS_CONTENT_ARTICLE_API_TOKEN`

## 4) Build image locally and push to Docker Hub
```bash
IMAGE_NAME=<dockerhub_username> \
IMAGE_REPO=careerpolitics-job-extractor \
IMAGE_TAG=v1 \
./scripts/dockerhub-build-push.sh
```

## 5) Deploy pushed image to Droplet
```bash
DROPLET_HOST=<droplet_ip> \
DROPLET_USER=root \
IMAGE_NAME=<dockerhub_username> \
IMAGE_REPO=careerpolitics-job-extractor \
IMAGE_TAG=v1 \
./scripts/droplet-deploy-image.sh
```

This script uploads:
- `docker-compose.droplet.yml`
- `.env.droplet`

Then it runs `docker compose pull` + `docker compose up -d` on Droplet.

## 6) Verify on Droplet
```bash
ssh root@<droplet_ip>
cd /opt/careerpolitics-job-extractor
docker compose -f docker-compose.droplet.yml ps
docker compose -f docker-compose.droplet.yml logs -f
curl http://localhost:8080/actuator/health
```

API endpoint test:
```bash
curl "http://<droplet_ip>:8080/api/careerpolitics/content/trends/discover?geo=IN&language=en-US&maxTrends=5"
```

## 7) Update deployment (new image tag)
```bash
IMAGE_NAME=<dockerhub_username> IMAGE_REPO=careerpolitics-job-extractor IMAGE_TAG=v2 ./scripts/dockerhub-build-push.sh
DROPLET_HOST=<droplet_ip> DROPLET_USER=root IMAGE_NAME=<dockerhub_username> IMAGE_REPO=careerpolitics-job-extractor IMAGE_TAG=v2 ./scripts/droplet-deploy-image.sh
```
