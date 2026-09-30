FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/*.jar app.jar

RUN useradd -r -u 1001 cloudvault

USER cloudvault

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]