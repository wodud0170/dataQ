package qualityexecutor.service.std;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public static final String TERM_ENG_NM_MISMATCH = "TERM_ENG_NM_MISMATCH";
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

            // 2. 전체 용어 메모리 로드
            List<StdTermsVo> allTerms = sqlSessionTemplate.selectList("terms.selectAllTermsForDiag");
            // KR 한글명 → 용어, ENG 영문명 → 용어
            Map<String, StdTermsVo> termsByKr  = new HashMap<>();
            Map<String, StdTermsVo> termsByEng = new HashMap<>();
            for (StdTermsVo t : allTerms) {
                if (t.getTermsNm() != null)        termsByKr.put(t.getTermsNm(), t);
                if (t.getTermsEngAbrvNm() != null) termsByEng.put(t.getTermsEngAbrvNm(), t);
                // 이형동의어도 KR 맵에 등록
                if (t.getAllophSynmLst() != null) {
                    for (String syn : t.getAllophSynmLst()) {
                        if (syn != null && !syn.isEmpty()) termsByKr.putIfAbsent(syn, t);
                    }
                }
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

                String attrNmKr = attr.getAttrNmKr();  // 컬럼 한글명
                String attrNm   = attr.getAttrNm();    // 컬럼 영문명
                String dataType = attr.getDataType();
                long   dataLen  = attr.getDataLen();

                StdTermsVo termByKr  = attrNmKr != null ? termsByKr.get(attrNmKr.trim())  : null;
                StdTermsVo termByEng = attrNm   != null ? termsByEng.get(attrNm.trim())   : null;

                if (termByKr == null && termByEng == null) {
                    // 한글명, 영문명 모두 용어 미존재
                    batch.add(buildResult(attr, TERM_NOT_EXIST, "용어 미존재", null, null));
                    resultCnt++;
                } else if (termByKr != null && termByEng == null) {
                    // 한글명은 용어에 있으나 영문명 불일치
                    batch.add(buildResult(attr, TERM_ENG_NM_MISMATCH, "영문명 불일치",
                            termByKr.getTermsEngAbrvNm(), attrNm));
                    resultCnt++;
                    // 도메인 비교도 수행 (한글명 기준 용어 사용)
                    resultCnt += checkDomain(attr, termByKr, dataType, dataLen, batch);
                } else if (termByKr == null) {
                    // 영문명은 용어에 있으나 한글명 불일치
                    batch.add(buildResult(attr, TERM_KR_NM_MISMATCH, "한글명 불일치",
                            termByEng.getTermsNm(), attrNmKr));
                    resultCnt++;
                    // 도메인 비교도 수행 (영문명 기준 용어 사용)
                    resultCnt += checkDomain(attr, termByEng, dataType, dataLen, batch);
                } else {
                    // 둘 다 일치 → 도메인 비교
                    resultCnt += checkDomain(attr, termByKr, dataType, dataLen, batch);
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

    private int checkDomain(StdDataModelAttrVo attr, StdTermsVo term, String dataType, long dataLen, List<StdDiagResultVo> batch) {
        if (term.getDomainNm() == null) return 0;
        int cnt = 0;
        String stdType = term.getDataType();
        long   stdLen  = term.getDataLen();

        if (stdType != null && dataType != null && !stdType.equalsIgnoreCase(dataType)) {
            batch.add(buildResult(attr, DATA_TYPE_MISMATCH, "데이터 타입 불일치", stdType, dataType));
            cnt++;
        }
        if (stdLen > 0 && dataLen > stdLen) {
            batch.add(buildResult(attr, DATA_LEN_MISMATCH, "데이터 길이 초과",
                    String.valueOf(stdLen), String.valueOf(dataLen)));
            cnt++;
        }
        return cnt;
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
