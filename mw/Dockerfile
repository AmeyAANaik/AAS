FROM eclipse-temurin:21-jdk AS build

WORKDIR /app
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr python3 python3-pip poppler-utils \
    && pip3 install --no-cache-dir --break-system-packages invoice2data camelot-py pandas pypdf \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar /app/mw.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/mw.jar"]
