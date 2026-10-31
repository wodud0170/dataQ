FROM eclipse-temurin:8-jre

# build context = 프로젝트 루트
WORKDIR /app

# JAR — 사전 빌드 필요: mvn package -DskipTests -T 1C
COPY q-center/target/q-center-0.0.1-SNAPSHOT.jar /app/app.jar

# drivers.xml 은 DBHandler 가 PWD 기준 lib/jdbc 를 본다.
COPY lib/drivers.xml /app/lib/drivers.xml
COPY lib/jdbc/      /app/lib/jdbc/

# 로그 디렉토리 (application.properties 의 log.path 가 c:/temp/logs 라
# 컨테이너에서는 환경변수 LOG_PATH 로 /app/logs 로 override)
RUN mkdir -p /app/logs

ENV ND_HOME=/app
ENV TZ=Asia/Seoul

# 28090 = HTTP, 28091 = WebSocket (server.http.port)
EXPOSE 28090 28091

# file.encoding=UTF-8 — 비밀번호 해시(SecurityManager.encryptSHA256)가 JVM 기본 문자셋으로 바이트를 만든다.
# 기본 문자셋이 다른 환경끼리 TB_USER 를 옮기면 한글 비밀번호 로그인이 맞지 않으므로 고정한다 (110번 1.10-S6-b)
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
