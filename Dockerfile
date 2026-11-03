# ============================================================
#  CareerNexus Backend - Dockerfile (Render Deployment)
#  Multi-stage build: Build with Maven, Run with JRE only
# ============================================================

# ── Stage 1: BUILD ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml first (layer cache: only re-downloads deps if pom changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: RUN ────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

# Non-root user for security (never run as root in prod)
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

# Copy only the JAR from build stage (no source, no Maven)
COPY --from=build /app/target/*.jar app.jar

# Create upload directories and set ownership
RUN mkdir -p uploads/assessments static/uploads/resumes static/uploads/images \
    && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Render uses PORT env variable — default to 8080
EXPOSE 8080

# JVM tuning for container environments (limits memory to container limits)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
