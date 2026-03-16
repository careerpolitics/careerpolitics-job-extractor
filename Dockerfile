# Build stage: compile Spring Boot jar inside the image build
FROM gradle:8.14-jdk17-alpine AS builder
WORKDIR /workspace

# Copy repository sources
COPY . .

# Build executable boot jar (skip tests in container build)
RUN chmod +x gradlew \
    && ./gradlew clean bootJar -x test --no-daemon \
    && cp build/libs/*.jar /workspace/app.jar

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="CareerPolitics Team <support@careerpolitics.com>"
LABEL version="1.0.0"
LABEL description="CareerPolitics Government Job Scraper"

RUN addgroup -S careerpolitics && adduser -S careerpolitics -G careerpolitics
RUN mkdir -p /app && chown careerpolitics:careerpolitics /app
WORKDIR /app

COPY --from=builder /workspace/app.jar /app/careerpolitics-scraper.jar

USER careerpolitics
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/careerpolitics-scraper.jar"]
