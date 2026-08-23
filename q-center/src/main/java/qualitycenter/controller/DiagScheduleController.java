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
 * <p>일반 사용자 허용: list / detail / logs / cronPreview</p>
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

    @GetMapping("/{scheduleId}")
    public DiagScheduleVo detail(@RequestParam String scheduleId) {
        return sqlSessionTemplate.selectOne("diagSchedule.selectScheduleById", scheduleId);
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
            Map<String, String> params = new HashMap<>();
            params.put("scheduleId", scheduleId);
            params.put("logId",      log.getLogId());
            WebClientHandler webClient = new WebClientHandler(
                    NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/diag/schedule/runNow");
            // postData 는 Mono 이지만 본 엔드포인트는 동기 반환이라 block().
            // 실패 시 LOG 를 ERROR 로 즉시 마감.
            try {
                webClient.postData(
                        sessionService.getUserId(),
                        Objects.toString(request.getSession().getAttribute("SSID"), null),
                        params
                ).block();
            } catch (Exception we) {
                Map<String, Object> p = new HashMap<>();
                p.put("logId",        log.getLogId());
                p.put("execStatus",   "ERROR");
                p.put("errorMsg",     "[CONFIG] q-executor 호출 실패: " + we.getMessage());
                p.put("diagJobId",    null);
                p.put("structDiagId", null);
                sqlSessionTemplate.update("diagSchedule.updateLogFinish", p);
                // LOG 만 마감하면 스케줄 목록의 "마지막 실행" 이 계속 빈칸으로 남는다.
                // 정상 완료 경로(DiagSchedulerRunner.markFinish)와 동일하게 LAST_EXEC_* 도 갱신.
                Map<String, Object> pSc = new HashMap<>();
                pSc.put("scheduleId", scheduleId);
                pSc.put("status",     "ERROR");
                pSc.put("logId",      log.getLogId());
                sqlSessionTemplate.update("diagSchedule.updateScheduleLastExec", pSc);
                throw we;
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

    @GetMapping("/logs/{logId}")
    public DiagScheduleLogVo logDetail(@RequestParam String logId) {
        return sqlSessionTemplate.selectOne("diagSchedule.selectLogById", logId);
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
        if (!sessionService.isAdmin()) {
            throw new IllegalAccessException("관리자 권한 필요");
        }
    }

    private void validate(DiagScheduleVo vo) {
        if (vo.getScheduleNm() == null || vo.getScheduleNm().trim().isEmpty())
            throw new IllegalArgumentException("스케줄명 필수");
        if (vo.getDiagType() == null) throw new IllegalArgumentException("진단 유형 필수");
        if (!"STANDARD".equals(vo.getDiagType()) && !"STRUCT".equals(vo.getDiagType())
                && !"BOTH".equals(vo.getDiagType()))
            throw new IllegalArgumentException("진단 유형은 STANDARD/STRUCT/BOTH");
        if (vo.getDataModelId() == null) throw new IllegalArgumentException("데이터모델 필수");

        String type = vo.getScheduleType() == null ? "SIMPLE" : vo.getScheduleType();
        if ("SIMPLE".equals(type)) {
            if (vo.getRepeatCycle() == null) throw new IllegalArgumentException("반복 주기 필수");
            if (vo.getRepeatTime() == null || !vo.getRepeatTime().matches("^[0-2]\\d:[0-5]\\d$"))
                throw new IllegalArgumentException("실행 시각(HH:mm) 형식 오류");
            if ("WEEKLY".equals(vo.getRepeatCycle()) && vo.getRepeatDayOfWeek() == null)
                throw new IllegalArgumentException("요일 필수");
            if ("MONTHLY".equals(vo.getRepeatCycle()) && vo.getRepeatDayOfMonth() == null)
                throw new IllegalArgumentException("일자 필수");
        } else if ("CRON".equals(type)) {
            if (vo.getCronExpr() == null) throw new IllegalArgumentException("Cron 표현식 필수");
            try { CronExpression.parse(vo.getCronExpr()); }
            catch (Exception e) { throw new IllegalArgumentException("Cron 표현식 오류: " + e.getMessage()); }
        } else {
            throw new IllegalArgumentException("SCHEDULE_TYPE 은 SIMPLE|CRON");
        }
    }
}
