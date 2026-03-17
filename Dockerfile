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
LABEL org.opencontainers.image.title="careerpolitics-job-extractor"
LABEL org.opencontainers.image.description="CareerPolitics scraper service (Spring Boot + Selenium + Chrome)"

# Install Google Chrome + runtime dependencies for headless Selenium
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gnupg \
    unzip \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libgbm1 \
    libgtk-3-0 \
    libnss3 \
    libx11-xcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    libxss1 \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL https://dl.google.com/linux/linux_signing_key.pub \
    | gpg --dearmor -o /usr/share/keyrings/google-linux.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-linux.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
    > /etc/apt/sources.list.d/google.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# Non-root runtime user
RUN addgroup --system careerpolitics \
    && adduser --system --ingroup careerpolitics careerpolitics

WORKDIR /app
COPY --from=builder /workspace/app.jar /app/careerpolitics-scraper.jar

RUN mkdir -p /tmp/chrome \
    && chown -R careerpolitics:careerpolitics /tmp/chrome /app

USER careerpolitics

EXPOSE 8080

# Use actuator endpoint as runtime health signal
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/careerpolitics-scraper.jar"]
