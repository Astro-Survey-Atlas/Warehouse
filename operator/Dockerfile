FROM eclipse-temurin:17-jre

WORKDIR /app
COPY target/operator-0.1.0-SNAPSHOT-runner.jar /app/operator.jar

USER 10001
ENTRYPOINT ["java", "-jar", "/app/operator.jar"]
