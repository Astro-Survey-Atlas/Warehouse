FROM eclipse-temurin:17-jre

WORKDIR /app
COPY target/scanner-cli-0.1.0-SNAPSHOT-runner.jar /app/scanner-cli.jar

ENTRYPOINT ["java", "-jar", "/app/scanner-cli.jar"]
