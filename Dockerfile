# syntax=docker/dockerfile:1
# Same build shape as imdb-federation's subgraphs: Boot tools-jarmode extract
# plus a CDS training run to cut Cloud Run cold-start time.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre AS extract
WORKDIR /app
COPY --from=build /workspace/target/imdb-policy-service-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted
# Training run exits at context refresh (no Mongo connection needed)
RUN cd extracted && java -XX:ArchiveClassesAtExit=app.jsa \
    -Dspring.context.exit=onRefresh -jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=extract /app/extracted/ ./
ENV JAVA_TOOL_OPTIONS="-XX:SharedArchiveFile=app.jsa -XX:MaxRAMPercentage=70"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
