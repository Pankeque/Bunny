FROM gradle:8.7-jdk17 AS builder
USER root
WORKDIR /app
COPY backend/ backend/
RUN gradle -p backend installDist --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /app/backend/build/install/bunny-backend/ ./
EXPOSE 8080
ENTRYPOINT ["/app/bin/bunny-backend"]
