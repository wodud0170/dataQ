FROM timescale/timescaledb:2.10.0-pg13
ENV POSTGRES_USER=admin
ENV POSTGRES_PASSWORD=admin!123
ENV POSTGRES_DB=postgres
COPY initdb/ /docker-entrypoint-initdb.d/
