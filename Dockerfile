# Multi-stage build for better image size and security
FROM openjdk:17-jdk-slim AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Gradle wrapper files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .

# Make the gradlew script executable
RUN chmod +x gradlew

# Copy the source code
COPY src src

# Build the application
RUN ./gradlew build -x test

# Runtime stage
FROM openjdk:17-jdk-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create a non-root user for security
RUN groupadd -r foodybuddy && useradd -r -g foodybuddy foodybuddy

# Set the working directory inside the container
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/foodybuddy-orders-0.0.1-SNAPSHOT.jar app.jar

# Change ownership to non-root user
RUN chown foodybuddy:foodybuddy app.jar

# Switch to non-root user
USER foodybuddy

# Expose the port the app runs on
EXPOSE 8081

# Add health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# Set the Spring profile for production
ENV SPRING_PROFILES_ACTIVE=prod

# Run the application with optimized JVM settings
CMD ["java", "-Xmx512m", "-Xms256m", "-XX:+UseG1GC", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
