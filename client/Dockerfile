FROM gradle:jdk17 as builder
WORKDIR /app
COPY client/gradle gradle
COPY client/build.gradle .
COPY client/settings.gradle .
COPY client/src src
RUN gradle clean compileJava
RUN ls -alR .
ENTRYPOINT [ "gradle", "run" ]
