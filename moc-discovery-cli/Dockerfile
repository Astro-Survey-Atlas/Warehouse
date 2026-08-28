FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/moc-discovery-cli-0.1.0-SNAPSHOT-runner.jar /app/moc-discovery-cli.jar
ENTRYPOINT ["java", "-jar", "/app/moc-discovery-cli.jar"]
