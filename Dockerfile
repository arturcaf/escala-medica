FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY escala-medica.jar app.jar
RUN mkdir -p /app/data
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
