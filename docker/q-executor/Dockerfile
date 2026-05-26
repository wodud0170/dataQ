FROM eclipse-temurin:8-jre

# build context = 프로젝트 루트
WORKDIR /app

# JAR — 사전 빌드 필요: mvn package -DskipTests -T 1C
COPY q-executor/target/q-executor.jar /app/app.jar

# drivers.xml 은 DBHandler 가 PWD 기준 lib/jdbc 를 본다.
COPY lib/drivers.xml /app/lib/drivers.xml
COPY lib/jdbc/      /app/lib/jdbc/

# application.properties 의 log.path=/home/nd_suite/q-executor/logs 기본값에 맞춘다.
RUN mkdir -p /home/nd_suite/q-executor/logs

ENV ND_HOME=/app
ENV TZ=Asia/Seoul

EXPOSE 28098

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
