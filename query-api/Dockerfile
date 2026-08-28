# Copyright 2026 Astro Survey Atlas contributors.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY spatial-core/pom.xml spatial-core/pom.xml
COPY index-elasticsearch/pom.xml index-elasticsearch/pom.xml
COPY scanner-cli/pom.xml scanner-cli/pom.xml
COPY operator/pom.xml operator/pom.xml
COPY moc-discovery-cli/pom.xml moc-discovery-cli/pom.xml
COPY query-api/pom.xml query-api/pom.xml
COPY spatial-core/src spatial-core/src
COPY index-elasticsearch/src index-elasticsearch/src
COPY query-api/src query-api/src
RUN mvn -B -pl query-api -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/query-api/target/query-api-0.1.0-SNAPSHOT-runner.jar /app/query-api.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/query-api.jar"]
