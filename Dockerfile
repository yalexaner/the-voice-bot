# Build stage
FROM gradle:8.8-jdk21 AS build

WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/

# Download dependencies for better layer caching
RUN gradle dependencies --no-daemon

# Copy source code and build
COPY src/ src/
RUN gradle shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install ffmpeg for audio conversion and curl for health checks
RUN apk add --no-cache ffmpeg curl

# Create non-root user
RUN addgroup -g 1000 voicebot && \
    adduser -D -s /bin/sh -u 1000 -G voicebot voicebot

# Set up directories
RUN mkdir -p /app/data /app/backups && \
    chown -R voicebot:voicebot /app

# Copy built application
COPY --from=build /app/build/libs/app.jar /app/app.jar
RUN chown voicebot:voicebot /app/app.jar

# Switch to non-root user
USER voicebot
WORKDIR /app

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f -H "X-Admin-Token: ${ADMIN_HTTP_TOKEN}" http://localhost:8080/health || exit 1

# Expose port
EXPOSE 8080

# Run application
CMD ["java", "-jar", "app.jar"]