FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY escala-medica.jar app.jar
# Data directory will be mounted as volume on Railway
RUN mkdir -p /app/data
EXPOSE 8080
ENV PORT=8080
CMD ["java", "-jar", "app.jar"]
