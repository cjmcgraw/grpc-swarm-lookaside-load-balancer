FROM gradle:jdk17 as builder
WORKDIR /app
COPY client/gradle gradle
COPY client/build.gradle .
COPY client/settings.gradle .
COPY client/src .
RUN --mount=type=cache,target=/root/.gradle \
    gradle clean jar
RUN ls -alR .
ENTRYPOINT [ "tail", "-f", "/dev/null" ]
#ENTRYPOINT [ "java", "-cp", "main/java", "github.cjmcgraw.dnstester.DnsTesterClient" ]
