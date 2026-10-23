"""개발 DB 에 psql 을 부르는 명령 앞부분.

PC1 은 개발 DB 가 dataq-db 컨테이너, PC2 는 네이티브 PostgreSQL(localhost:25433)이다.
PC2 에서는 DATAQ_PSQL=native 와 PGPASSWORD 를 환경변수로 주고 돌린다.
-w — 비밀번호가 없으면 입력을 기다리지 않고 바로 실패한다(기다리면 시험마다 30초씩 멈춘다).
"""
import os

if os.environ.get("DATAQ_PSQL") == "native":
    os.environ["PGCLIENTENCODING"] = "UTF8"
    PSQL = [r"C:\Program Files\PostgreSQL\13\bin\psql.exe",
            "-h", "localhost", "-p", "25433", "-U", "admin", "-d", "postgres", "-w"]
else:
    PSQL = ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres"]
