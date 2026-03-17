# Deploy to a DigitalOcean Droplet (Docker Compose)

This guide replaces App Platform deployment and runs the scraper directly on a Droplet.

## 1) Create Droplet
- Ubuntu 22.04 LTS
- At least 2 vCPU / 4 GB RAM recommended (Selenium + Chrome are memory heavy)
- Open inbound ports:
  - `22` for SSH
  - `8080` for direct API access (or `80/443` if reverse proxy is used)

## 2) Install Docker + Compose plugin
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

## 3) Clone project and configure env
```bash
git clone <YOUR_REPO_URL>
cd careerpolitics-job-extractor
cp .env.droplet.example .env.droplet
nano .env.droplet
```

Set at least:
- `OPENROUTER_API_KEY`
- `CAREERPOLITICS_CONTENT_ARTICLE_API_URL`
- `CAREERPOLITICS_CONTENT_ARTICLE_API_TOKEN`

## 4) Build and run
```bash
docker compose -f docker-compose.droplet.yml up -d --build
```

## 5) Verify
```bash
docker compose -f docker-compose.droplet.yml ps
docker compose -f docker-compose.droplet.yml logs -f
curl http://localhost:8080/actuator/health
```

API endpoint test:
```bash
curl "http://localhost:8080/api/careerpolitics/content/trends/discover?geo=IN&language=en-US&maxTrends=5"
```

## 6) Update deployment
```bash
git pull
docker compose -f docker-compose.droplet.yml up -d --build
```

## 7) Optional systemd auto-start (on boot)
```bash
sudo tee /etc/systemd/system/careerpolitics-job-extractor.service > /dev/null <<'UNIT'
[Unit]
Description=CareerPolitics Job Extractor
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
WorkingDirectory=/home/<YOUR_USER>/careerpolitics-job-extractor
RemainAfterExit=yes
ExecStart=/usr/bin/docker compose -f docker-compose.droplet.yml up -d --build
ExecStop=/usr/bin/docker compose -f docker-compose.droplet.yml down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
UNIT

sudo systemctl daemon-reload
sudo systemctl enable --now careerpolitics-job-extractor
```
