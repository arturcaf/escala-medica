FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy JAR
COPY escala-medica.jar app.jar

# Create data directory
RUN mkdir -p /app/data

# Render uses PORT env variable automatically
EXPOSE 8080

# Start - PORT is set by Render automatically
CMD ["java", "-jar", "app.jar"]
