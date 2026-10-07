# syntax=docker/dockerfile:1

# ---- Build: compila e empacota o JAR com o wrapper do Maven ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# O mvnw baixa o .zip do Maven (SHA-256 fixado em maven-wrapper.properties); sem unzip ele
# trocaria para .tar.gz e a validação do hash falharia.
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*

# Camada de dependências: só muda quando o pom.xml ou o wrapper mudam.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package \
    && cp target/kontra-backend-*.jar /app/aplicacao.jar

# ---- Runtime: JRE enxuto, usuário não root ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S kontra && adduser -S -G kontra kontra
COPY --from=build /app/aplicacao.jar aplicacao.jar

# Locale da JVM em pt-BR (validação fora de requisição — ADR-013, "Locale fixo").
ENV JAVA_TOOL_OPTIONS="-Duser.language=pt -Duser.country=BR"

USER kontra
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/aplicacao.jar"]
