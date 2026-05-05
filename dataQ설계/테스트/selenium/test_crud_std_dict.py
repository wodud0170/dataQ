"""
표준 사전 CRUD 통합 테스트 — 관리자 (space) 기준

검증 (각 항목 등록→조회→수정→삭제 후 cleanup):
  C1. 단어 (TB_WORD)
  C2. 도메인 그룹 (TB_DOMAIN_GRP)
  C3. 도메인 분류 (TB_DOMAIN_CLSF)
"""
import base64, sys, time, traceback
import requests

BASE = "http://localhost:28091"
results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def login():
    s = requests.Session()
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(BASE + "/login", data={"id": "space", "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200
    return s


def main():
    s = login()
    suffix = str(int(time.time()))[-6:]

    # C1. 단어 CRUD
    def _c1():
        nm = f"테스트단어_{suffix}"
        abrv = f"TST{suffix}"
        # CREATE
        r = s.post(BASE + "/api/std/createWord", json={
            "wordNm": nm, "wordEngAbrvNm": abrv, "wordEngNm": f"Test{suffix}",
            "wordDesc": "CRUD test", "wordClsfYn": "N", "domainClsfNm": None,
            "allophSynmLst": [], "forbdnWordLst": [], "reqSysCd": None
        }, timeout=10)
        assert r.status_code == 200, f"createWord status={r.status_code}"
        # READ
        r2 = s.get(BASE + "/api/std/getWordByEngAbrvNm", params={"wordEngAbrvNm": abrv}, timeout=10)
        d = r2.json()
        assert isinstance(d, dict) and d.get("wordNm") == nm, f"readback fail: {d}"
        wid = d.get("wordId") or d.get("id")
        assert wid, f"wordId 없음: {d}"
        print(f"  CREATE OK wordId={wid}")
        # UPDATE
        d["wordDesc"] = "CRUD test updated"
        r3 = s.post(BASE + "/api/std/updateWord", json=d, timeout=10)
        assert r3.status_code == 200, f"updateWord status={r3.status_code}"
        r4 = s.get(BASE + "/api/std/getWordByEngAbrvNm", params={"wordEngAbrvNm": abrv}, timeout=10)
        d2 = r4.json()
        assert d2.get("wordDesc") == "CRUD test updated", f"update reflect fail: {d2.get('wordDesc')}"
        print(f"  UPDATE OK")
        # DELETE — VO 의 id 필드로 전달
        r5 = s.post(BASE + "/api/std/deleteWords", json=[{"id": wid}], timeout=10)
        assert r5.status_code == 200, f"delete status={r5.status_code}"
        rb = r5.json() or {}
        rc = rb.get("resultCode") or (rb.get("resultInfo", {}) or {}).get("code")
        assert rc in (200, "200"), f"delete fail: {rb}"
        r6 = s.get(BASE + "/api/std/getWordByEngAbrvNm", params={"wordEngAbrvNm": abrv}, timeout=10)
        # 삭제 후 응답: 빈 body 또는 null 또는 wordNm 없는 객체 — 모두 OK
        try:
            rj = r6.json() or {}
        except Exception:
            rj = {}
        assert (not rj) or not rj.get("wordNm"), f"delete 후 잔존: {rj}"
        print(f"  DELETE OK")
    step("C1. 단어 CRUD (create→read→update→delete)", _c1)

    # C2. 도메인 그룹 CRUD
    def _c2():
        nm = f"테스트그룹_{suffix}"
        # CREATE
        r = s.post(BASE + "/api/std/createDomainGroup", json={
            "domainGrpNm": nm,
            "domainGrpDesc": "test group",
            "commStndYn": "N"
        }, timeout=10)
        assert r.status_code == 200, f"createDomainGroup status={r.status_code}"
        body = r.json()
        rc = body.get("resultCode") or (body.get("resultInfo", {}) or {}).get("code")
        assert rc in ("200", 200), f"createDomainGroup failed: {body}"
        # READ
        r2 = s.get(BASE + "/api/std/getDomainGroupList", timeout=10)
        rows = r2.json() or []
        match = next((x for x in rows if x.get("domainGrpNm") == nm), None)
        assert match, f"등록한 도메인 그룹 조회 실패: {nm}"
        gid = match.get("id") or match.get("domainGrpId")
        print(f"  CREATE OK id={gid}")
        # UPDATE — updateDomainGroup mapper 가 갱신하는 컬럼: DOMAIN_GRP_NM, COMM_STND_YN
        match["commStndYn"] = "Y"
        r3 = s.post(BASE + "/api/std/updateDomainGroup", json=match, timeout=10)
        assert r3.status_code == 200
        r4 = s.get(BASE + "/api/std/getDomainGroupList", timeout=10)
        rows2 = r4.json() or []
        m2 = next((x for x in rows2 if x.get("domainGrpNm") == nm), None)
        assert m2 and m2.get("commStndYn") == "Y", f"update reflect fail: {m2}"
        print(f"  UPDATE OK")
        # DELETE
        r5 = s.post(BASE + "/api/std/deleteDomainGroups", json=[{"id": gid}], timeout=10)
        assert r5.status_code == 200
        r6 = s.get(BASE + "/api/std/getDomainGroupList", timeout=10)
        gone = not any(x.get("domainGrpNm") == nm for x in (r6.json() or []))
        assert gone, "삭제 후에도 잔존"
        print(f"  DELETE OK")
    step("C2. 도메인 그룹 CRUD", _c2)

    # C3. 도메인 분류 CRUD — domain_grp_nm 필요. 미리 그룹 생성 후 사용 → cleanup
    def _c3():
        gnm = f"분류용그룹_{suffix}"
        # 임시 그룹
        s.post(BASE + "/api/std/createDomainGroup", json={
            "domainGrpNm": gnm, "domainGrpDesc": "temp", "commStndYn": "N"
        }, timeout=10)
        rg = s.get(BASE + "/api/std/getDomainGroupList", timeout=10).json() or []
        grp = next((x for x in rg if x.get("domainGrpNm") == gnm), None)
        assert grp, "임시 그룹 생성 실패"
        grp_id = grp.get("id")

        nm = f"테스트분류_{suffix}"
        # CREATE
        r = s.post(BASE + "/api/std/createDomainClassification", json={
            "domainClsfNm": nm, "domainGrpNm": gnm, "domainClsfDesc": "test clsf", "commStndYn": "N"
        }, timeout=10)
        assert r.status_code == 200, f"createDomainClassification status={r.status_code}"
        # READ
        r2 = s.get(BASE + "/api/std/getDomainClassificationList", timeout=10)
        rows = r2.json() or []
        match = next((x for x in rows if x.get("domainClsfNm") == nm), None)
        assert match, f"등록한 분류 조회 실패: {nm}"
        cid = match.get("id") or match.get("domainClsfId")
        print(f"  CREATE OK id={cid}")
        # UPDATE
        match["domainClsfDesc"] = "updated"
        r3 = s.post(BASE + "/api/std/updateDomainClassification", json=match, timeout=10)
        assert r3.status_code == 200
        # DELETE
        r5 = s.post(BASE + "/api/std/deleteDomainClassifications", json=[{"id": cid}], timeout=10)
        assert r5.status_code == 200
        # cleanup 임시 그룹
        s.post(BASE + "/api/std/deleteDomainGroups", json=[{"id": grp_id}], timeout=10)
        print(f"  UPDATE/DELETE OK + cleanup")
    step("C3. 도메인 분류 CRUD", _c3)


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.0f}초)\n{'='*60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
