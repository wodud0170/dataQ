package qualityexecutor.service.std;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ndata.quality.model.std.QualRunningLockVo;

import lombok.extern.slf4j.Slf4j;

/**
 * 83번 §6 — 품질 진단 부하 안정성 인프라.
 *
 * <p>두 가지 보호 메커니즘:</p>
 * <ol>
 *   <li><b>컬럼 단위 application-level mutex</b> — TB_QUAL_RUNNING_LOCK 에 INSERT (ON CONFLICT DO NOTHING).
 *       동일 (DM_ID, OBJ_NM, ATTR_NM) 이 점유 중이면 SKIP.</li>
 *   <li><b>글로벌 동시 N건 제한</b> — Java Semaphore (default 5).</li>
 * </ol>
 *
 * <p>운영 DB 락 절대 X. 우리 메타DB 안에서만 동작.</p>
 */
@Slf4j
@Service
public class QualLockService {

    @Autowired
    private SqlSessionTemplate sqlSession;

    /** 동시 외부 DB 진단 최대 N건 (default 5). property 로 override 가능. */
    @Value("${qual.diag.global.max:5}")
    private int globalMax;

    private Semaphore globalSlot;

    @PostConstruct
    public void init() {
        this.globalSlot = new Semaphore(globalMax, true);
        log.info(">> QualLockService initialized — globalMax={}", globalMax);
        // 시작 시 stale lock 정리 (이전 q-executor 비정상 종료 흔적)
        cleanupStale();
    }

    // ─────────────────────────────────────────────────────────
    // 컬럼 단위 lock
    // ─────────────────────────────────────────────────────────

    /**
     * lock 획득 시도. true = 획득 성공, false = 이미 점유 중 (호출 측 SKIP).
     */
    public boolean acquire(String dmId, String objNm, String attrNm,
                           String diagId, String userId) {
        QualRunningLockVo vo = new QualRunningLockVo();
        vo.setDmId(dmId);
        vo.setObjNm(objNm);
        vo.setAttrNm(attrNm);
        vo.setDiagId(diagId);
        vo.setUserId(userId);
        // startDt 는 매퍼에서 COALESCE(... , now())
        int affected = sqlSession.insert("qualLock.acquire", vo);
        if (affected == 0) {
            log.info(">> Lock SKIP — {}.{}.{} 이미 점유 중", dmId, objNm, attrNm);
        }
        return affected > 0;
    }

    /** lock 해제 — try-finally 에서 무조건 호출. */
    public void release(String dmId, String objNm, String attrNm) {
        QualRunningLockVo vo = new QualRunningLockVo();
        vo.setDmId(dmId);
        vo.setObjNm(objNm);
        vo.setAttrNm(attrNm);
        sqlSession.delete("qualLock.release", vo);
    }

    /** 현재 점유 중 lock 목록 (모니터링용). */
    public List<QualRunningLockVo> listAll() {
        return sqlSession.selectList("qualLock.listAll");
    }

    /** 점유 건수 (Throttle 모니터링용). */
    public int countAll() {
        return sqlSession.selectOne("qualLock.countAll");
    }

    // ─────────────────────────────────────────────────────────
    // Stale lock 정리
    // ─────────────────────────────────────────────────────────

    /**
     * START_DT 가 30분 이상 경과한 row 정리.
     * 5분마다 cron + 시작 시 1회.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)  // 5분
    public void cleanupStale() {
        try {
            int n = sqlSession.delete("qualLock.cleanupStale");
            if (n > 0) {
                log.info(">> Stale lock cleanup — {} rows", n);
            }
        } catch (Exception e) {
            log.warn(">> Stale lock cleanup 실패 — {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // 글로벌 동시 N건 제한 (Semaphore)
    // ─────────────────────────────────────────────────────────

    /**
     * 글로벌 슬롯 획득 시도. 1초 안에 못 얻으면 false.
     * 호출 측은 false 시 "글로벌 진단 큐 가득 — 잠시 후 재시도" 안내.
     */
    public boolean tryAcquireGlobalSlot() {
        try {
            return globalSlot.tryAcquire(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void releaseGlobalSlot() {
        globalSlot.release();
    }

    public int globalAvailable() {
        return globalSlot.availablePermits();
    }

    public int globalMax() {
        return globalMax;
    }
}
