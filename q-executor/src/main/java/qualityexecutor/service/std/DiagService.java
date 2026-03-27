package qualityexecutor.service.std;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDiagJobVo;
import com.ndata.quality.model.std.StdDiagResultVo;
import com.ndata.quality.model.std.StdTermsVo;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
@Service
public class DiagService implements Runnable {

    // 진단 유형 코드
    public static final String TERM_NOT_EXIST       = "TERM_NOT_EXIST";
    public static final String TERM_KR_NM_MISMATCH  = "TERM_KR_NM_MISMATCH";
    public static final String DATA_TYPE_MISMATCH   = "DATA_TYPE_MISMATCH";
    public static final String DATA_LEN_MISMATCH    = "DATA_LEN_MISMATCH";

    // 중지 플래그 맵: diagJobId → stop 요청 여부
    private static final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    private String diagJobId;
    private String clctId;
    private String dataModelId;
    private String userId;

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    public DiagService(String diagJobId, String clctId, String dataModelId, String userId) {
        this.diagJobId    = diagJobId;
        this.clctId       = clctId;
        this.dataModelId  = dataModelId;
        this.userId       = userId;
    }

    /** q-executor DiagController가 호출 */
    public static void requestStop(String diagJobId) {
        AtomicBoolean flag = stopFlags.get(diagJobId);
        if (flag != null) flag.set(true);
        else stopFlags.put(diagJobId, new AtomicBoolean(true));
    }

    private boolean isStopped() {
        AtomicBoolean flag = stopFlags.get(diagJobId);
        return flag != null && flag.get();
    }

    @Override
    public void run() {
        stopFlags.put(diagJobId, new AtomicBoolean(false));
        log.info(">> DiagService started: jobId={}, clctId={}", diagJobId, clctId);

        try {
            // 1. 상태 RUNNING으로 변경
            updateStatus("RUNNING");

            // 2. 전체 용어 메모리 로드 (영문명 기준 매칭)
            List<StdTermsVo> allTerms = sqlSessionTemplate.selectList("terms.selectAllTermsForDiag");
            Map<String, StdTermsVo> termsByEng = new HashMap<>();
            for (StdTermsVo t : allTerms) {
                if (t.getTermsEngAbrvNm() != null) termsByEng.put(t.getTermsEngAbrvNm(), t);
            }

            // 3. 진단 대상 컬럼 목록 로드
            List<StdDataModelAttrVo> attrs = sqlSessionTemplate.selectList(
                "datamodel.selectDataModelAttrListByClctId", clctId);
            int total = attrs.size();
            int processCnt = 0;
            int resultCnt  = 0;

            // 총 건수 먼저 DB 업데이트
            updateProgress(total, 0, 0);

            // 4. 컬럼별 진단
            List<StdDiagResultVo> batch = new ArrayList<>();
            for (StdDataModelAttrVo attr : attrs) {
                if (isStopped()) {
                    log.info(">> DiagService stopped: jobId={}", diagJobId);
                    break;
                }

                String attrNmKr = attr.getAttrNmKr();  // 컬럼 한글명 (COMMENTS)
                String attrNm   = attr.getAttrNm();    // 컬럼 영문명
                String dataType = attr.getDataType();
                long   dataLen  = attr.getDataLen();
                int    dataDecimalLen = attr.getDataDecimalLen();

                // STEP 1. 영문명 기준 용어 존재 확인
                StdTermsVo termByEng = attrNm != null ? termsByEng.get(attrNm.trim()) : null;

                if (termByEng == null) {
                    // 영문명 기준 용어 미존재 → 진단 종료
                    batch.add(buildResult(attr, TERM_NOT_EXIST, "용어 미존재", null, attrNm));
                    resultCnt++;
                } else {
                    // STEP 2. 한글명 일치 확인
                    // - 표준 용어에 한글명이 있는 경우, 실제 한글명이 null/빈값이면 불일치로 판정
                    // - 표준 용어에 한글명이 없으면 비교 스킵
                    String stdTermsNm = termByEng.getTermsNm();
                    if (stdTermsNm != null && !stdTermsNm.trim().isEmpty()) {
                        String actualKr = (attrNmKr != null) ? attrNmKr.trim() : "";
                        if (!stdTermsNm.trim().equals(actualKr)) {
                            batch.add(buildResult(attr, TERM_KR_NM_MISMATCH, "한글명 불일치",
                                    stdTermsNm, attrNmKr));
                            resultCnt++;
                        }
                    }

                    // STEP 3. 도메인 검증 (타입·길이)
                    resultCnt += checkDomain(attr, termByEng, dataType, dataLen, dataDecimalLen, batch);
                }

                processCnt++;

                // 50건마다 배치 인서트 및 진행 상황 업데이트
                if (batch.size() >= 50) {
                    flushBatch(batch);
                    updateProgress(total, processCnt, resultCnt);
                }
            }

            // 나머지 결과 flush
            if (!batch.isEmpty()) flushBatch(batch);

            if (isStopped()) {
                // 중지 시 결과 삭제
                sqlSessionTemplate.delete("diag.deleteDiagResultsByJobId", diagJobId);
                updateProgress(total, processCnt, 0);
                updateStatus("STOPPED");
            } else {
                updateProgress(total, processCnt, resultCnt);
                updateStatus("DONE");
            }
        } catch (Exception e) {
            log.error(">> DiagService error: jobId={}", diagJobId, e);
            updateStatus("ERROR");
        } finally {
            stopFlags.remove(diagJobId);
        }
    }

    private int checkDomain(StdDataModelAttrVo attr, StdTermsVo term, String dataType, long dataLen, int dataDecimalLen, List<StdDiagResultVo> batch) {
        if (term.getDomainNm() == null) return 0;
        int cnt = 0;
        String stdType       = term.getDataType();
        long   stdLen        = term.getDataLen();

        // 타입 비교 (case-insensitive, 동의어 처리: DATE=DATETIME 등)
        if (stdType != null && dataType != null && !isTypeEquivalent(stdType, dataType)) {
            batch.add(buildResult(attr, DATA_TYPE_MISMATCH, "데이터 타입 불일치", stdType, dataType));
            cnt++;
        }
        // 길이 비교 (소수점 자릿수는 비교하지 않음)
        if (stdLen > 0 && dataLen != stdLen) {
            batch.add(buildResult(attr, DATA_LEN_MISMATCH, "데이터 길이 불일치",
                    String.valueOf(stdLen), String.valueOf(dataLen)));
            cnt++;
        }
        return cnt;
    }

    /** 데이터 타입 동의어 비교 (Oracle DATE = DATETIME 등) */
    private boolean isTypeEquivalent(String stdType, String actualType) {
        if (stdType.equalsIgnoreCase(actualType)) return true;
        String s = stdType.toUpperCase();
        String a = actualType.toUpperCase();
        // DATE ↔ DATETIME
        if ((s.equals("DATE") && a.equals("DATETIME")) || (s.equals("DATETIME") && a.equals("DATE"))) return true;
        // CHAR ↔ VARCHAR ↔ VARCHAR2 (문자열 계열)
        if (isStringFamily(s) && isStringFamily(a)) return true;
        // NUMBER ↔ NUMERIC ↔ DECIMAL
        if (isNumericFamily(s) && isNumericFamily(a)) return true;
        return false;
    }

    private boolean isStringFamily(String type) {
        return type.equals("CHAR") || type.equals("VARCHAR") || type.equals("VARCHAR2");
    }

    private boolean isNumericFamily(String type) {
        return type.equals("NUMBER") || type.equals("NUMERIC") || type.equals("DECIMAL");
    }

    private StdDiagResultVo buildResult(StdDataModelAttrVo attr, String diagType, String diagDetail, String stdValue, String actualValue) {
        StdDiagResultVo r = new StdDiagResultVo();
        r.setDiagJobId(diagJobId);
        r.setObjNm(attr.getObjNm());
        r.setAttrNm(attr.getAttrNm());
        r.setAttrNmKr(attr.getAttrNmKr());
        r.setDiagType(diagType);
        r.setDiagDetail(diagDetail);
        r.setStdValue(stdValue);
        r.setActualValue(actualValue);
        return r;
    }

    private void flushBatch(List<StdDiagResultVo> batch) {
        for (StdDiagResultVo r : batch) {
            sqlSessionTemplate.insert("diag.insertDiagResult", r);
        }
        batch.clear();
    }

    private void updateStatus(String status) {
        StdDiagJobVo job = new StdDiagJobVo();
        job.setDiagJobId(diagJobId);
        job.setStatus(status);
        sqlSessionTemplate.update("diag.updateDiagJobStatus", job);
    }

    private void updateProgress(int total, int processCnt, int resultCnt) {
        StdDiagJobVo job = new StdDiagJobVo();
        job.setDiagJobId(diagJobId);
        job.setTotalCnt(total);
        job.setProcessCnt(processCnt);
        job.setResultCnt(resultCnt);
        sqlSessionTemplate.update("diag.updateDiagJobProgress", job);
    }
}
