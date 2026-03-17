# CareerPolitics Job Scraper – Deployment Guide

This document provides instructions to build the Docker image for the CareerPolitics Job Scraper and deploy it on a DigitalOcean droplet.

---

## 📦 Prerequisites

- **Local machine**:
    - [Docker Desktop](https://www.docker.com/products/docker-desktop/) (with Docker Compose)
    - [Git](https://git-scm.com/) (optional)
    - Java 17+ and Gradle (if you need to rebuild the application)
    - A [Docker Hub](https://hub.docker.com/) account (or any container registry)

- **DigitalOcean**:
    - An active DigitalOcean account
    - SSH key pair (for droplet access)

---

## 🏗️ Build and Push the Application Image

The application image is built using **Jib** (Google’s container build tool for Java). Jib integrates with Gradle and does not require a Docker daemon on your build machine.

### 1. Clone the repository (if not already done)

```bash
git clone https://github.com/careerpolitics/careerpolitics-job-extractor.git
cd careerpolitics-job-extractor
```

### 2. Build the image and push to Docker Hub

Make sure you are logged in to Docker Hub:

```bash
docker login
```

Then run the Gradle Jib task:

```bash
./gradlew jib
```

This will:
- Build the application image using the base image `selenium/standalone-chrome:latest`
- Push the image to Docker Hub under the repository `muraridevv/careerpolitics-job-scraper` (or the one defined in your Jib configuration).

> **Note**: The image tag is taken from `project.version` (e.g., `1.0-SNAPSHOT`). You can override it by setting environment variables `IMAGE_NAME` and `IMAGE_TAG`.

After a successful push, your image is available at:  
`docker.io/muraridevv/careerpolitics-job-scraper:1.0-SNAPSHOT`

---

## ☁️ Deploy on DigitalOcean Droplet

### 1. Create a Droplet

- Log in to your DigitalOcean control panel.
- Click **Create → Droplet**.
- Choose an **Ubuntu 22.04 LTS** image (or later).
- Select a plan (e.g., Basic, $6/month with 1 GB RAM – adjust based on your needs).
- Add your **SSH key** for secure access.
- Give your droplet a hostname (e.g., `careerpolitics-scraper`).
- Click **Create Droplet**.

Once created, note the droplet’s **IPv4 address**.

### 2. SSH into the Droplet

```bash
ssh root@<your-droplet-ip>
```

### 3. Install Docker and Docker Compose

Run the following commands on the droplet:

```bash
# Update package list
apt update

# Install prerequisites
apt install -y apt-transport-https ca-certificates curl software-properties-common

# Add Docker's official GPG key and repository
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Verify installations
docker --version
docker compose version
```

### 4. Prepare the Deployment Directory

Create a directory for the project and navigate into it:

```bash
mkdir /opt/careerpolitics
cd /opt/careerpolitics
```

### 5. Copy Configuration Files

You need the `docker-compose.yaml` and `.env` files on the droplet.  
Use `scp` from your local machine to transfer them:

```bash
scp docker-compose.yaml root@<your-droplet-ip>:/opt/careerpolitics/
scp .env root@<your-droplet-ip>:/opt/careerpolitics/
```

> **Note**: Make sure your `.env` file contains all necessary environment variables (e.g., database credentials, API keys). Keep it secure.

### 6. Start the Services

On the droplet, inside `/opt/careerpolitics`, run:

```bash
docker compose up -d
```

This will:
- Pull the `selenium/standalone-chrome:latest` image (if not already present)
- Pull your application image from Docker Hub
- Start both containers in detached mode

Check that everything is running:

```bash
docker compose ps
```

You should see both services with status `Up`.

### 7. Verify the Application

Your application should be accessible at `http://<your-droplet-ip>:8080`.

You can test with:

```bash
curl http://localhost:8080/actuator/health   # if Spring Boot Actuator is enabled
```

Or open the IP in a browser.

---

## 🔧 Useful Commands for Maintenance

- **View logs**:
  ```bash
  docker compose logs -f app
  ```
- **Stop services**:
  ```bash
  docker compose down
  ```
- **Restart services**:
  ```bash
  docker compose restart
  ```
- **Update the application**:
    - Rebuild and push a new image locally.
    - On the droplet, run:
      ```bash
      docker compose pull app
      docker compose up -d
      ```

---

## 🌐 Optional: Set Up a Domain and SSL

If you have a domain, you can point it to your droplet’s IP and use **Let’s Encrypt** to enable HTTPS. A common approach is to put an Nginx reverse proxy in front of the application.

Example steps:

1. Install Nginx:
   ```bash
   apt install nginx
   ```
2. Configure a reverse proxy to `localhost:8080`.
3. Use Certbot to obtain SSL certificates:
   ```bash
   apt install certbot python3-certbot-nginx
   certbot --nginx -d your-domain.com
   ```

---

## 🧹 Clean Up

To remove all containers, networks, and volumes:

```bash
docker compose down -v
```

To delete the application images:

```bash
docker rmi muraridevv/careerpolitics-job-scraper:1.0-SNAPSHOT
docker rmi selenium/standalone-chrome:latest
```

---

## 📄 License

This project is licensed under the MIT License.

---

## 🆘 Troubleshooting

- **Port already in use**: Make sure port 8080 is free on the droplet. Stop any other service using it.
- **Selenium connection refused**: Ensure the `selenium` service is healthy and the app can reach it via the service name (`selenium`) on port 4444 (Docker Compose internal networking).
- **Permission denied for `.env`**: Set correct permissions: `chmod 600 .env`.
- **Docker pull fails on droplet**: Check internet connectivity and proxy settings.

For further assistance, open an issue on the GitHub repository.