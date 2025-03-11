# Use the official Gradle image with JDK 17 as the build environment
FROM gradle:8.13-jdk17-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the built JAR from the build environment
COPY build/libs/*.jar app.jar

# Expose the port that the application will run on
EXPOSE 19305

# Set environment variables if needed (e.g., for ports)
ENV PORT=19305

# Run the application
CMD ["java", "-jar", "app.jar"]
