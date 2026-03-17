# =========================
# Build stage
# =========================
FROM gradle:8.14-jdk17 AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew \
    && ./gradlew clean bootJar -x test --no-daemon \
    && cp build/libs/*.jar /workspace/app.jar


# =========================
# Runtime stage
# =========================
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="CareerPolitics Team <support@careerpolitics.com>"
LABEL version="1.0.0"
LABEL description="CareerPolitics Government Job Scraper"

# Install runtime dependencies required by Chrome/Selenium.
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gnupg \
    unzip \
    wget \
    xdg-utils \
    fonts-liberation \
    fonts-noto-color-emoji \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libu2f-udev \
    libvulkan1 \
    libx11-6 \
    libx11-xcb1 \
    libxcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxkbcommon0 \
    libxrandr2 \
    libxrender1 \
    libxshmfence1 \
    libxss1 \
    libxtst6 \
    procps \
    && rm -rf /var/lib/apt/lists/*

# Add Google Chrome repository and install Chrome.
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-linux.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-linux.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
       > /etc/apt/sources.list.d/google.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# Create dedicated app user + writable home/cache directories.
RUN addgroup --system careerpolitics \
    && adduser --system --ingroup careerpolitics --home /home/careerpolitics careerpolitics \
    && mkdir -p /home/careerpolitics/.cache/selenium /tmp/chrome /app \
    && chown -R careerpolitics:careerpolitics /home/careerpolitics /tmp/chrome /app

ENV HOME=/home/careerpolitics \
    CHROME_BIN=/usr/bin/google-chrome \
    CAREERPOLITICS_CONTENT_SELENIUM_HEADLESS=true

WORKDIR /app

COPY --from=builder /workspace/app.jar /app/careerpolitics-scraper.jar

USER careerpolitics

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/careerpolitics-scraper.jar"]
