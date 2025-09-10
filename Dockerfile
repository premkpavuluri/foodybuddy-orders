# Use the official OpenJDK 17 image as the base image
FROM openjdk:17-jdk-slim

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

# Expose the port the app runs on
EXPOSE 8081

# Run the application
CMD ["java", "-jar", "build/libs/foodybuddy-orders-0.0.1-SNAPSHOT.jar"]
