FROM timescale/timescaledb:2.10.0-pg13

ENV POSTGRES_USER=admin
ENV POSTGRES_PASSWORD=admin!123
ENV POSTGRES_DB=postgres

# 기존 base image 의 PGDATA(/var/lib/postgresql/data)는 VOLUME 선언 때문에
# 이미지 빌드 시 박아둔 데이터가 anonymous volume 에 가려진다.
# → PGDATA 를 다른 경로로 옮겨서 VOLUME 선언을 우회.
ENV PGDATA=/var/lib/postgresql/dataq

# 현재 운영중인 dataq-db 의 PGDATA 스냅샷을 그대로 박는다.
# docker stop dataq-db → 호스트에서 추출한 데이터.
COPY --chown=postgres:postgres pgdata/ /var/lib/postgresql/dataq/

# PostgreSQL 은 PGDATA 권한이 0700 이 아니면 기동 거부.
RUN chmod 700 /var/lib/postgresql/dataq

EXPOSE 5432
