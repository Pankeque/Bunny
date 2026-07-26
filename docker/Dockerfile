FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY backend/build.gradle.kts backend/settings.gradle.kts ./
COPY backend/src ./src
RUN apt-get update && apt-get install -y gradle
RUN gradle :backend:shadowJar

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=builder /app/backend/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
