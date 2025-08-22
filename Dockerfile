FROM eclipse-temurin:17-jre-alpine

# Add labels for CareerPolitics
LABEL maintainer="CareerPolitics Team <support@careerpolitics.com>"
LABEL version="1.0.0"
LABEL description="CareerPolitics Government Job Scraper"

# Create app user
RUN addgroup -S careerpolitics && adduser -S careerpolitics -G careerpolitics

# Create app directory
RUN mkdir -p /app && chown careerpolitics:careerpolitics /app
WORKDIR /app

# Copy JAR file
COPY build/libs/careerpolitics-scraper.jar /app/careerpolitics-scraper.jar

# Switch to non-root user
USER careerpolitics

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/careerpolitics-scraper.jar"]