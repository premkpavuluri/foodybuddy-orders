# Optimized Orders Service - expects pre-built JAR
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the pre-built JAR (expects it to be in build/libs/)
COPY build/libs/foodybuddy-orders-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8081

# Optimized JVM settings for fast startup
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Run the application
CMD ["java", "-jar", "app.jar"]