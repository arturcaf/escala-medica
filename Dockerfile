FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY escala-medica.jar .
RUN mkdir -p /app/data
EXPOSE 8080
CMD ["java", "-Dserver.port=${PORT:-8080}", "-jar", "escala-medica.jar"]
