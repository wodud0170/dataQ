-- dataQ설계/sync/PC_align_2026-11-01_aprv_req_pick.sql — 108 §4.33 보완 7 (104번 WP1 결재자 "요청자 지정")
-- 결재선 단계 결재자 방식에 REQ_PICK(요청자 지정 — 요청할 때 요청자가 그 단계 결재자 한 명을 고름)을 더한다.
-- 칸 형식은 그대로(varchar(20), 값 검사는 앱) — 컬럼 주석 2개만 바꾼다.
-- 적용: PC1 docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-11-01_aprv_req_pick.sql
--       PC2 네이티브 psql 로 같은 파일 (psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …).
-- 멱등(두 번 적용해도 같다). 적용 뒤 pg_dump 의 두 주석 블록을 덤프 2종(DDL_full_schema.sql·docker/dataq-db/initdb/01_schema.sql)에 반영.
SET search_path TO quality;

BEGIN;
COMMENT ON COLUMN quality.tb_aprv_line_step.aprvr_tp IS '결재자 방식 — USER(지정 사용자), ROLE(역할의 사용자), ADMIN(관리자 전원), ORG_HEAD(요청자 부서장), REQ_PICK(요청자 지정 — 요청할 때 요청자가 고름, 108 §4.33)';
COMMENT ON COLUMN quality.tb_aprv_line_step.aprvr_id IS 'USER 면 사용자 ID, ROLE 이면 역할 ID, ADMIN·ORG_HEAD·REQ_PICK 이면 NULL';
COMMIT;
