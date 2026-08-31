# Multi-stage build for OfferPilot (面壁) backend

# Stage 1: Build with Maven + JDK 21
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build
COPY . .
RUN chmod +x mvnw && ./mvnw package -DskipTests -B

# Stage 2: Runtime with JRE 21
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]