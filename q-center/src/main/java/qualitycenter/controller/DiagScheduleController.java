package qualitycenter.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.ndata.common.handler.WebClientHandler;
import com.ndata.common.message.Response;
import com.ndata.quality.common.PermCode;
import com.ndata.common.message.RestResult;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.model.std.DiagScheduleLogVo;
import com.ndata.quality.model.std.DiagScheduleVo;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

/**
 * 진단 스케줄러 관리 API (65번 문서)
 *
 * <p>관리자 전용: create / update / delete / toggle / runNow</p>
 * <p>일반 사용자 허용: list / logs / cronPreview</p>
 *
 * <p>실제 스케줄 평가/실행은 q-executor 의 DiagSchedulerRunner 가 담당.
 * 본 컨트롤러는 메타데이터 CRUD + runNow 트리거만 다룬다.</p>
 */
@Tag(name = "DiagSchedule", description = "진단 스케줄러")
@Slf4j
@RestController
@RequestMapping("/api/diag/schedule")
public class DiagScheduleController {

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private com.ndata.quality.service.StdDictService dictService;

    // ==================== 조회 ====================

    @GetMapping("/list")
    public List<DiagScheduleVo> list(
            @RequestParam(required = false) String dataModelId,
            @RequestParam(required = false) String diagType,
            @RequestParam(required = false) String useYn) {
        Map<String, Object> p = new HashMap<>();
        p.put("dataModelId", dataModelId);
        p.put("diagType",    diagType);
        p.put("useYn",       useYn);
        return sqlSessionTemplate.selectList("diagSchedule.selectScheduleList", p);
    }

    // ==================== 생성/수정/삭제 (관리자) ====================

    @PostMapping("/create")
    public Response create(@RequestBody DiagScheduleVo vo) {
        Response res = new Response();
        try {
            assertAdmin();
            validate(vo);
            vo.setScheduleId(StringUtils.getUUID());
            vo.setCretUserId(sessionService.getUserId());
            if (vo.getUseYn() == null) vo.setUseYn("Y");
            sqlSessionTemplate.insert("diagSchedule.insertSchedule", vo);
            res.setContents(vo.getScheduleId());
            res.setResultInfo(RestResult.CODE_200.getCode(), "등록 완료");
        } catch (IllegalAccessException e) {
            res.setResultInfo(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> schedule create failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @PostMapping("/update")
    public Response update(@RequestBody DiagScheduleVo vo) {
        Response res = new Response();
        try {
            assertAdmin();
            if (vo.getScheduleId() == null) throw new IllegalArgumentException("scheduleId 필수");
            validate(vo);
            vo.setUpdtUserId(sessionService.getUserId());
            int cnt = sqlSessionTemplate.update("diagSchedule.updateSchedule", vo);
            if (cnt == 0) throw new IllegalArgumentException("대상 스케줄 없음");
            res.setResultInfo(RestResult.CODE_200.getCode(), "수정 완료");
        } catch (IllegalAccessException e) {
            res.setResultInfo(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> schedule update failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @PostMapping("/toggle")
    public Response toggle(@RequestBody Map<String, String> body) {
        Response res = new Response();
        try {
            assertAdmin();
            String scheduleId = body.get("scheduleId");
            String useYn      = body.get("useYn");
            if (scheduleId == null || useYn == null) throw new IllegalArgumentException("scheduleId/useYn 필수");
            Map<String, Object> p = new HashMap<>();
            p.put("scheduleId",  scheduleId);
            p.put("useYn",       useYn);
            p.put("updtUserId",  sessionService.getUserId());
            sqlSessionTemplate.update("diagSchedule.updateScheduleUseYn", p);
            res.setResultInfo(RestResult.CODE_200.getCode(), "토글 완료");
        } catch (IllegalAccessException e) {
            res.setResultInfo(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> schedule toggle failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @PostMapping("/delete")
    public Response delete(@RequestBody Map<String, String> body) {
        Response res = new Response();
        try {
            assertAdmin();
            String scheduleId = body.get("scheduleId");
            if (scheduleId == null) throw new IllegalArgumentException("scheduleId 필수");
            sqlSessionTemplate.delete("diagSchedule.deleteSchedule", scheduleId);
            res.setResultInfo(RestResult.CODE_200.getCode(), "삭제 완료");
        } catch (IllegalAccessException e) {
            res.setResultInfo(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> schedule delete failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    // ==================== 즉시 실행 (관리자) ====================

    /** 즉시 실행: LOG(RUNNING) insert 후 q-executor 로 실제 실행 요청 위임 */
    @PostMapping("/runNow")
    public Response runNow(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Response res = new Response();
        try {
            assertAdmin();
            String scheduleId = body.get("scheduleId");
            if (scheduleId == null) throw new IllegalArgumentException("scheduleId 필수");
            DiagScheduleVo sc = sqlSessionTemplate.selectOne(
                    "diagSchedule.selectScheduleById", scheduleId);
            if (sc == null) throw new IllegalArgumentException("스케줄 없음");

            // 110 6.10-12a — 화면에서 돌린 진단(Job·구조 진단 이력)이 진행 중이면 겹쳐 띄우지 않는다 (화면 실행과 같은 409, LOG 안 만듦)
            String diagType = sc.getDiagType();
            int active = 0;
            if ("STANDARD".equals(diagType) || "BOTH".equals(diagType)) {
                int jobCnt = sqlSessionTemplate.selectOne("diag.selectActiveDiagJobCount", sc.getDataModelId());
                active += jobCnt;
            }
            if ("STRUCT".equals(diagType) || "BOTH".equals(diagType)) {
                int structCnt = sqlSessionTemplate.selectOne("structdiag.selectActiveStructDiagCount", sc.getDataModelId());
                active += structCnt;
            }
            if (active > 0) {
                res.setResultInfo(409, "이미 진행 중인 진단이 있습니다.");
                return res;
            }

            // 1) LOG (RUNNING) 먼저 insert
            DiagScheduleLogVo log = new DiagScheduleLogVo();
            log.setLogId(StringUtils.getUUID());
            log.setScheduleId(scheduleId);
            log.setScheduleNmSnapshot(sc.getScheduleNm());
            log.setTriggerType("MANUAL");
            log.setDiagType(sc.getDiagType());
            log.setExecStatus("RUNNING");
            sqlSessionTemplate.insert("diagSchedule.insertLog", log);

            // 2) q-executor 에 실행 요청 (동일 logId 전달)
            // 110 6.10-13b — 실행자는 누른 사람 (런처가 Job·구조 진단 이력·범정부 메타·배치 실행자로 기록)
            Map<String, String> params = new HashMap<>();
            params.put("scheduleId", scheduleId);
            params.put("logId",      log.getLogId());
            params.put("userId",     sessionService.getUserId());
            WebClientHandler webClient = new WebClientHandler(
                    NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/diag/schedule/runNow");
            // postData 는 Mono 이지만 본 엔드포인트는 동기 반환이라 block().
            // 실패 시 LOG 를 ERROR 로 즉시 마감.
            Response er;
            try {
                er = webClient.postData(
                        sessionService.getUserId(),
                        Objects.toString(request.getSession().getAttribute("SSID"), null),
                        params
                ).block();
            } catch (Exception we) {
                finishRunNowError(scheduleId, log.getLogId(), "[CONFIG] q-executor 호출 실패: " + we.getMessage());
                throw we;
            }
            // 110 6.10-7a·7b — executor 가 거부하면(스케줄 없음·[CONFIG] 등) 실패로 알리고 RUNNING LOG 를 마감한다.
            // 예전엔 응답 코드를 안 봐 "실행 요청 전송 완료" 로 보이고 LOG 는 60분 뒤 타임아웃까지 RUNNING 이었다.
            if (er.getResultCode() != RestResult.CODE_200.getCode()) {
                finishRunNowError(scheduleId, log.getLogId(), "[EXECUTOR] " + er.getResultMessage());
                res.setResultInfo(RestResult.CODE_500.getCode(), er.getResultMessage());
                return res;
            }

            res.setContents(log.getLogId());
            res.setResultInfo(RestResult.CODE_200.getCode(), "실행 요청 전송 완료");
        } catch (IllegalAccessException e) {
            res.setResultInfo(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            DiagScheduleController.log.error(">> runNow failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    /**
     * 즉시 실행 실패 마감 — LOG 가 아직 RUNNING 일 때만 ERROR 로 닫고 스케줄 LAST_EXEC_* 도 갱신한다.
     * launcher 가 기동 실패를 이미 마감했으면(그쪽 사유가 더 정확) 덮지 않는다.
     * LOG 만 마감하면 스케줄 목록의 "마지막 실행" 이 빈칸으로 남으므로 정상 완료 경로(DiagSchedulerRunner.markFinish)와 같이 갱신.
     */
    private void finishRunNowError(String scheduleId, String logId, String errorMsg) {
        Map<String, Object> p = new HashMap<>();
        p.put("logId",        logId);
        p.put("execStatus",   "ERROR");
        p.put("errorMsg",     errorMsg);
        p.put("diagJobId",    null);
        p.put("structDiagId", null);
        p.put("onlyRunning",  true);
        if (sqlSessionTemplate.update("diagSchedule.updateLogFinish", p) == 0) return;
        Map<String, Object> pSc = new HashMap<>();
        pSc.put("scheduleId", scheduleId);
        pSc.put("status",     "ERROR");
        pSc.put("logId",      logId);
        sqlSessionTemplate.update("diagSchedule.updateScheduleLastExec", pSc);
    }

    // ==================== 이력 조회 ====================

    @GetMapping("/logs")
    public List<DiagScheduleLogVo> logList(
            @RequestParam(required = false) String scheduleId,
            @RequestParam(required = false) String execStatus,
            @RequestParam(required = false) String fromDt,
            @RequestParam(required = false) String toDt,
            @RequestParam(required = false, defaultValue = "200") Integer limit) {
        Map<String, Object> p = new HashMap<>();
        p.put("scheduleId", scheduleId);
        p.put("execStatus", execStatus);
        p.put("fromDt",     fromDt);
        p.put("toDt",       toDt);
        p.put("limit",      limit);
        return sqlSessionTemplate.selectList("diagSchedule.selectLogList", p);
    }

    // ==================== CRON 미리보기 ====================

    @PostMapping("/cronPreview")
    public Response cronPreview(@RequestBody Map<String, String> body) {
        Response res = new Response();
        try {
            String expr = body.get("cronExpr");
            if (expr == null || expr.trim().isEmpty()) throw new IllegalArgumentException("cronExpr 필수");
            CronExpression cron = CronExpression.parse(expr.trim());
            List<String> next = new ArrayList<>();
            LocalDateTime cursor = LocalDateTime.now();
            for (int i = 0; i < 5; i++) {
                LocalDateTime n = cron.next(cursor);
                if (n == null) break;
                next.add(n.toString());
                cursor = n;
            }
            Map<String, Object> content = new HashMap<>();
            content.put("cronExpr", expr);
            content.put("next", next);
            res.setContents(new Gson().toJson(content));
            res.setResultInfo(RestResult.CODE_200.getCode(), "OK");
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, "cron 표현식 오류: " + e.getMessage());
        } catch (Exception e) {
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    // ==================== 유틸 ====================

    private void assertAdmin() throws IllegalAccessException {
        if (!sessionService.hasPerm(PermCode.SCHEDULE_ADMIN)) {
            throw new IllegalAccessException("권한이 없습니다 (스케줄 관리 권한 필요)");
        }
    }

    private void validate(DiagScheduleVo vo) {
        if (vo.getScheduleNm() == null || vo.getScheduleNm().trim().isEmpty())
            throw new IllegalArgumentException("스케줄명 필수");
        if (vo.getDiagType() == null) throw new IllegalArgumentException("진단 유형 필수");
        if (!java.util.Arrays.asList("STANDARD", "STRUCT", "BOTH", "GOV_META", "COMPLIANCE_SNAPSHOT", "UNUSED_STD").contains(vo.getDiagType()))
            throw new IllegalArgumentException("진단 유형은 STANDARD/STRUCT/BOTH/GOV_META/COMPLIANCE_SNAPSHOT/UNUSED_STD");
        // 104번 WP9-67 — 준수 현황 스냅샷·미사용 표준은 모델을 비울 수 있다 (스냅샷은 사용 중 모델 전부, 미사용 표준은 기본 사전)
        boolean batch = "COMPLIANCE_SNAPSHOT".equals(vo.getDiagType()) || "UNUSED_STD".equals(vo.getDiagType());
        if (vo.getDataModelId() != null && vo.getDataModelId().trim().isEmpty()) vo.setDataModelId(null);
        if (!batch && vo.getDataModelId() == null) throw new IllegalArgumentException("데이터모델 필수");
        // 110 6.10-2 — 표준사전은 표준화 진단(STANDARD·BOTH)에만 쓴다. 그 밖의 유형은 화면이 보낸 사전을 버린다
        boolean std = "STANDARD".equals(vo.getDiagType()) || "BOTH".equals(vo.getDiagType());
        if (!std) vo.setDictId(null);

        if (batch) {
            if (vo.getDataModelId() != null && sqlSessionTemplate.selectOne("datamodel.selectDataModelById", vo.getDataModelId()) == null)
                throw new IllegalArgumentException("데이터 모델을 찾을 수 없습니다.");
        } else if ("GOV_META".equals(vo.getDiagType())) {
            // 104번 WP5-78 — 범정부 메타는 표준사전 대신 기관 설정과 모델 데이터소스(= DB)가 있어야 한다
            if (sqlSessionTemplate.selectOne("govMeta.selectConf") == null)
                throw new IllegalArgumentException("범정부 메타 기관 설정이 없습니다. [범정부 메타 > 설정] 에서 먼저 저장해 주세요.");
            java.util.Map<String, Object> dm = sqlSessionTemplate.selectOne("govMeta.selectModel", vo.getDataModelId());
            if (dm == null || dm.get("dsId") == null || String.valueOf(dm.get("dsId")).trim().isEmpty())
                throw new IllegalArgumentException("데이터소스가 없는 모델은 범정부 메타 대상이 아닙니다.");
        } else {
            boolean struct = "STRUCT".equals(vo.getDiagType())   || "BOTH".equals(vo.getDiagType());
            java.util.Map<String, Object> dm = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", vo.getDataModelId());
            if (dm == null) throw new IllegalArgumentException("데이터 모델을 찾을 수 없습니다.");
            // 110 6.10-11a — 구조 변경 진단은 실 DB 와 비교하므로 데이터 소스가 있어야 한다.
            // 등록 때 막지 않으면 실행마다 ScheduledDiagLauncher.startStructDiag 의 [CONFIG] 로 실패했다.
            if (struct) {
                Object dsId = dm.get("dataModelDsId");
                if (dsId == null || String.valueOf(dsId).trim().isEmpty())
                    throw new IllegalArgumentException("데이터 소스가 없는 논리 모델은 구조 변경 진단을 예약할 수 없습니다.");
            }
            // 110 6.10-2 — 스케줄에 고른 사전은 사용 중인 사전이어야 한다. 비우면 실행 때 모델의 사전
            if (std) {
                if (vo.getDictId() != null && vo.getDictId().trim().isEmpty()) vo.setDictId(null);
                if (vo.getDictId() != null && dictService.list().stream().noneMatch(d -> vo.getDictId().equals(d.get("dictId"))))
                    throw new IllegalArgumentException("사용할 수 없는 표준사전입니다.");
                if (dictService.resolve(vo.getDictId(), vo.getDataModelId()) == null) {
                    // 98번 — 예약 실행에는 사전을 고를 사람이 없다. 사전을 안 고르고 모델에도 사전이 없으면
                    // 실행 시점에 무엇으로 진단할지 정할 수 없으므로 등록 단계에서 막는다.
                    // (실행할 때 실패시키면 사용자가 원인을 알기 어렵다)
                    throw new IllegalArgumentException(
                        "이 데이터 모델에 표준사전이 지정되어 있지 않아 예약 진단을 등록할 수 없습니다."
                        + " [데이터 모델 관리] 에서 모델을 수정해 표준사전을 먼저 지정해 주세요.");
                }
            }
        }

        String type = vo.getScheduleType() == null ? "SIMPLE" : vo.getScheduleType();
        if ("SIMPLE".equals(type)) {
            if (vo.getRepeatCycle() == null) throw new IllegalArgumentException("반복 주기 필수");
            // 110 6.10-11b — 24~29시가 통과하던 정규식을 00~23시로
            if (vo.getRepeatTime() == null || !vo.getRepeatTime().matches("^([01]\\d|2[0-3]):[0-5]\\d$"))
                throw new IllegalArgumentException("실행 시각(HH:mm) 형식 오류");
            if ("WEEKLY".equals(vo.getRepeatCycle())) {
                if (vo.getRepeatDayOfWeek() == null) throw new IllegalArgumentException("요일 필수");
                if (vo.getRepeatDayOfWeek() < 1 || vo.getRepeatDayOfWeek() > 7)
                    throw new IllegalArgumentException("요일은 1(월)~7(일) 이어야 합니다.");
            }
            if ("MONTHLY".equals(vo.getRepeatCycle())) {
                if (vo.getRepeatDayOfMonth() == null) throw new IllegalArgumentException("일자 필수");
                if (vo.getRepeatDayOfMonth() < 1 || vo.getRepeatDayOfMonth() > 28)
                    throw new IllegalArgumentException("일자는 1~28 이어야 합니다.");
            }
        } else if ("CRON".equals(type)) {
            if (vo.getCronExpr() == null) throw new IllegalArgumentException("Cron 표현식 필수");
            try { CronExpression.parse(vo.getCronExpr()); }
            catch (Exception e) { throw new IllegalArgumentException("Cron 표현식 오류: " + e.getMessage()); }
        } else {
            throw new IllegalArgumentException("SCHEDULE_TYPE 은 SIMPLE|CRON");
        }
    }
}
