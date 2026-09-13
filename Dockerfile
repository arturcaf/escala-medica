FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY escala-medica.jar .
# /app/data will be mounted as Railway Volume for data persistence
RUN mkdir -p /app/data
EXPOSE 8080
CMD ["java", "-jar", "escala-medica.jar"]
