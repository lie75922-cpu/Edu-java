FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/target/edu-backend-*.jar /app/app.jar

RUN groupadd --system edu \
    && useradd --system --gid edu --home-dir /app --shell /usr/sbin/nologin edu \
    && chown -R edu:edu /app

USER edu
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
