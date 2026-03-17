# 🚀 CareerPolitics Job Scraper – Deployment & Domain Setup Guide

This guide covers:

* Building & pushing Docker image (via Jib)
* Deploying on a DigitalOcean droplet
* Running with Docker Compose
* Exposing via custom domain + HTTPS

---

# 📦 Prerequisites

### Local Machine

* Docker Desktop (with Compose)
* Java 17+
* Gradle
* Docker Hub account

### Server (DigitalOcean)

* Ubuntu 22.04+
* SSH access

---

# 🏗️ Build & Push Docker Image

### 1. Clone repo

```bash
git clone https://github.com/careerpolitics/careerpolitics-job-extractor.git
cd careerpolitics-job-extractor
```

### 2. Login to Docker

```bash
docker login
```

### 3. Build & Push (Jib)

```bash
./gradlew jib
```

👉 Image pushed to:

```
muraridevv/careerpolitics-job-scraper:<tag>
```

---

# ☁️ Deploy on DigitalOcean

## 1. Create Droplet

* Ubuntu 22.04
* Add SSH key
* Note IP (e.g., `139.59.62.214`)

---

## 2. SSH into server

```bash
ssh root@<your-ip>
```

---

## 3. Install Docker

```bash
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

Verify:

```bash
docker --version
docker compose version
```

---

## 4. Setup Project Directory

```bash
mkdir /opt/careerpolitics
cd /opt/careerpolitics
```

---

## 5. Copy Files

From local machine:

```bash
scp docker-compose.yaml root@<ip>:/opt/careerpolitics/
scp .env root@<ip>:/opt/careerpolitics/
```

---

## 6. Start Services

```bash
docker compose up -d
```

Check:

```bash
docker compose ps
```

---

## 7. Verify App

```
http://<your-ip>:8080
```

---

# 🔄 Updating Application

```bash
docker compose pull
docker compose up -d --force-recreate
```

---

# 🌐 Setup Domain + HTTPS

## 🎯 Goal

```
http://<ip>:8080 → https://qa.careerpolitics.com
```

---

## 1️⃣ DNS Configuration

Add A record:

```
qa → <server-ip>
```

---

## 2️⃣ Install Nginx

```bash
apt install nginx -y
```

---

## 3️⃣ Configure Reverse Proxy

```bash
nano /etc/nginx/sites-available/qa.careerpolitics.com
```

Paste:

```nginx
server {
    listen 80;
    server_name qa.careerpolitics.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

Enable:

```bash
ln -s /etc/nginx/sites-available/qa.careerpolitics.com /etc/nginx/sites-enabled/
nginx -t
systemctl restart nginx
```

---

## 4️⃣ Test HTTP

```
http://qa.careerpolitics.com
```

If redirecting to HTTPS early:

* Try incognito
* Disable Cloudflare proxy (if used)

---

## 5️⃣ Enable HTTPS (SSL)

```bash
apt install certbot python3-certbot-nginx -y
certbot --nginx -d qa.careerpolitics.com
```

---

## 6️⃣ Final URL

```
https://qa.careerpolitics.com
```

---

# 🔧 Useful Commands

### Logs

```bash
docker compose logs -f careerpolitics-scraper
```

### Restart

```bash
docker compose restart
```

### Stop

```bash
docker compose down
```

---

# ⚠️ Troubleshooting

### Port issue

```bash
lsof -i :8080
```

---

### Check Nginx

```bash
nginx -t
systemctl status nginx
```

---

### HTTPS not working

* Ensure ports open:

```bash
ufw allow 80
ufw allow 443
```

---

### Selenium issues

* Ensure service is running
* Check logs

---

# 🧠 Best Practices

* Do NOT expose port 8080 publicly (use Nginx)
* Store secrets in `.env`
* Use PostgreSQL instead of H2 for production

---

# 🔐 Optional: Protect QA

```nginx
auth_basic "Restricted";
auth_basic_user_file /etc/nginx/.htpasswd;
```

---

# 🧹 Cleanup

```bash
docker compose down -v
docker rmi muraridevv/careerpolitics-job-scraper
```

---

# ✅ Final Architecture

```
User → Domain (qa.careerpolitics.com)
     → Nginx (80/443)
     → Docker App (8080)
     → Selenium Container
```

---

# 🚀 Next Steps

* Add CI/CD (auto deploy)
* Schedule scraper (cron/queue)
* Add monitoring (Prometheus/Grafana)

---
