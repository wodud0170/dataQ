-- dataQ설계/sync/PC_align_2026-11-01_aprv_area_sys.sql — 108 §4.35 보완 8 (104번 WP1-51 결재 대상 확대 — 주제영역·시스템 설정)
-- 요청 유형 AREA(업무·주제영역)·SYS(시스템)를 더한다. 결재선이 켜져 있으면 변경을 바로 반영하지 않고 결재 문서에 담아 두었다가
-- 최종 승인 때 반영한다 — 담아 둘 칸 TB_APRV_DOC.REQ_PAYLOAD(text, JSON)와 주석.
-- 적용: PC1 docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-11-01_aprv_area_sys.sql
--       PC2 네이티브 psql 로 같은 파일 (psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …).
-- 멱등(두 번 적용해도 같다). 적용 뒤 pg_dump 블록(tb_aprv_doc·주석)을 덤프 2종(DDL_full_schema.sql·docker/dataq-db/initdb/01_schema.sql)에 반영.
SET search_path TO quality;

BEGIN;
ALTER TABLE quality.tb_aprv_doc ADD COLUMN IF NOT EXISTS req_payload text;
COMMENT ON COLUMN quality.tb_aprv_doc.req_payload IS '최종 승인 때 반영할 내용(JSON) — AREA·SYS 만 {action: SAVE·DELETE, kind: BIZ·SUBJ·SYS, data, before} (108 §4.35)';
COMMENT ON TABLE quality.tb_aprv_doc IS '결재 문서 (104번 WP1) — 표준사전 항목 하나, 데이터 모델 신청(SUBMISSION_ID) 하나, 또는 영역·시스템 변경 하나(108 §4.35)';
COMMENT ON COLUMN quality.tb_aprv_doc.target_id IS 'WORD·TERMS·CODE·DOMAIN 은 항목 ID, DM_CHANGE 는 SUBMISSION_ID, AREA 는 업무·주제영역 ID, SYS 는 시스템 코드';
COMMENT ON COLUMN quality.tb_aprv_line.req_tp IS '요청 유형 — WORD, TERMS, CODE, DOMAIN, DM_CHANGE, AREA(업무·주제영역), SYS(시스템)';
COMMIT;
