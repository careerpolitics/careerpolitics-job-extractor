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
# Runtime stage (FIXED)
# =========================
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="CareerPolitics Team <support@careerpolitics.com>"
LABEL version="1.0.0"
LABEL description="CareerPolitics Government Job Scraper"

# Install Chrome + dependencies
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    unzip \
    gnupg \
    ca-certificates \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libx11-xcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    xdg-utils \
    libgbm1 \
    libgtk-3-0 \
    libnss3 \
    libxss1 \
    && rm -rf /var/lib/apt/lists/*

# Add Google Chrome repo
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-linux.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-linux.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
    > /etc/apt/sources.list.d/google.list

# Install Chrome
RUN apt-get update && apt-get install -y google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# Create user
RUN addgroup --system careerpolitics && adduser --system --ingroup careerpolitics careerpolitics

WORKDIR /app

COPY --from=builder /workspace/app.jar /app/careerpolitics-scraper.jar

# Give permissions for Chrome temp usage
RUN mkdir -p /tmp/chrome && chown -R careerpolitics:careerpolitics /tmp

USER careerpolitics

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/careerpolitics-scraper.jar"]