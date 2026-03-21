FROM gradle:8.7-jdk21 AS builder
WORKDIR /workspace
COPY build.gradle settings.gradle gradle.properties gradlew gradlew.bat ./
COPY gradle gradle
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/careerpolitics-trending-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
