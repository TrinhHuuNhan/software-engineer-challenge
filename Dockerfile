FROM eclipse-temurin:11-jdk AS build

WORKDIR /app

COPY src/main/java/AggregatorApp.java .

RUN javac AggregatorApp.java


FROM eclipse-temurin:11-jre

WORKDIR /app

COPY --from=build /app/*.class .

ENTRYPOINT ["java", "AggregatorApp"]