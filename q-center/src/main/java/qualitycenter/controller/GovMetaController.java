package qualitycenter.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.bean.SecurityManager;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.service.GovMetaService;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP5-78 — 범정부 메타 (구현 ①).
 *
 * <p>설정(기관·연계, 업무분류체계, 코드 매핑), 정보시스템·DB 항목, 테이블·컬럼 항목, 생성 전 검증과 이력.
 * 조회는 로그인 사용자 누구나, 저장·검증은 범정부 메타 관리 권한(관리자 또는 GOV_META_ADMIN)이 필요하다.
 * 파일 생성·전송은 연계 규격을 받은 뒤 붙인다(구현 ②).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/govMeta")
public class GovMetaController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SqlSessionFactory sqlSessionFactory;
	@Autowired private SessionService sessionService;
	@Autowired private GovMetaService govMetaService;
	@Autowired SecurityManager securityUtils;

	private static final ObjectMapper OM = new ObjectMapper();

	/** 테이블 항목 엑셀 — 우리 양식 (업로드 양식을 몰라 화면 그리드와 같은 열) */
	private static final String[] TBL_XLS_HEADERS = {
		"모델 ID", "데이터 모델", "테이블소유자", "영문테이블명", "한글테이블명", "전송대상",
		"테이블유형", "발생주기", "공개/비공개여부", "부분공개·비공개 사유", "부분공개·비공개 관련근거", "개방데이터목록"
	};
	/** 컬럼 항목 엑셀 — 우리 양식 */
	private static final String[] COL_XLS_HEADERS = {
		"모델 ID", "테이블소유자", "영문테이블명", "영문컬럼명", "한글컬럼명", "데이터타입",
		"공개/비공개여부", "비공개 사유", "제약조건"
	};

	private Response denied() {
		if (sessionService.hasPerm(PermCode.GOV_META_ADMIN)) return null;
		Response r = new Response();
		r.setResultInfo(403, "권한이 없습니다 (범정부 메타 관리 권한 필요)");
		return r;
	}

	private Response ok(Object data) throws JsonProcessingException {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		r.setContents(OM.writeValueAsString(data));
		return r;
	}

	private Response fail(String msg) {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_500.getCode(), msg);
		return r;
	}

	private static String s(Object o) {
		return o == null ? null : o.toString().trim();
	}

	private static boolean empty(String v) {
		return v == null || v.isEmpty();
	}

	// ===== 기관·연계 설정 =====

	@GetMapping("/conf")
	public Mono<Response> conf() throws JsonProcessingException {
		Map<String, Object> c = sqlSessionTemplate.selectOne("govMeta.selectConf");
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("conf", c == null ? new LinkedHashMap<>() : c);
		m.put("codeGrps", GovMetaService.CODE_GRPS);
		return Mono.just(ok(m));
	}

	@PostMapping("/conf/save")
	public Mono<Response> saveConf(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		if (empty(s(body.get("instNm")))) return Mono.just(fail("기관명을 넣어 주세요."));
		String instCd = s(body.get("instCd"));
		if (!empty(instCd) && instCd.length() != 7) return Mono.just(fail("기관코드는 7자리입니다."));
		Map<String, Object> p = new HashMap<>(body);
		for (String k : Arrays.asList("sftpPort", "fileKeepCnt")) {
			String v = s(p.get(k));
			try {
				p.put(k, empty(v) ? null : Integer.valueOf(v));
			} catch (NumberFormatException e) {
				return Mono.just(fail((k.equals("sftpPort") ? "SFTP 포트" : "파일 보관 개수") + "는 숫자여야 합니다."));
			}
		}
		// 비밀번호는 바꿀 때만 온다 — 비면 저장된 값을 둔다
		String pwd = s(body.get("sftpPwd"));
		p.put("sftpPwd", empty(pwd) ? null : securityUtils.encryptStr(pwd));
		p.put("userId", sessionService.getUserId());
		sqlSessionTemplate.insert("govMeta.upsertConf", p);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		return Mono.just(res);
	}

	// ===== 업무분류체계(BRM) =====

	@PostMapping("/brm/list")
	public Mono<Response> brmList(@RequestBody Map<String, Object> filter) throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("govMeta.selectBrmList", filter)));
	}

	/** 기관 자체 BRM 저장 — {brmId?, brmNm, parentBrmId?}. 단계·경로는 상위에서 계산한다 (4단계까지) */
	@PostMapping("/brm/save")
	public Mono<Response> brmSave(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		String nm = s(body.get("brmNm"));
		if (empty(nm)) return Mono.just(fail("분류체계명을 넣어 주세요."));
		String brmId = s(body.get("brmId"));
		String parentId = s(body.get("parentBrmId"));
		Map<String, Object> p = new HashMap<>();
		int lvl = 1;
		String path = nm;
		if (!empty(parentId)) {
			Map<String, Object> parent = sqlSessionTemplate.selectOne("govMeta.selectBrm", parentId);
			if (parent == null) return Mono.just(fail("상위 분류체계를 찾을 수 없습니다."));
			lvl = ((Number) parent.get("brmLvl")).intValue() + 1;
			path = (parent.get("brmPath") == null ? parent.get("brmNm") : parent.get("brmPath")) + ">" + nm;
		}
		if (lvl > 4) return Mono.just(fail("업무분류체계는 4단계까지입니다."));
		if (empty(brmId)) {
			brmId = "INST_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		} else {
			Map<String, Object> cur = sqlSessionTemplate.selectOne("govMeta.selectBrm", brmId);
			if (cur != null && "GOV".equals(cur.get("brmSrc"))) return Mono.just(fail("정부기능분류체계에서 가져온 항목은 고칠 수 없습니다. 다시 반입하세요."));
		}
		p.put("brmId", brmId);
		p.put("brmNm", nm);
		p.put("parentBrmId", empty(parentId) ? null : parentId);
		p.put("brmLvl", lvl);
		p.put("brmPath", path);
		p.put("instCd", s(body.get("instCd")));
		p.put("brmSrc", "INST");
		p.put("useYn", empty(s(body.get("useYn"))) ? "Y" : s(body.get("useYn")));
		p.put("userId", sessionService.getUserId());
		sqlSessionTemplate.insert("govMeta.upsertBrm", p);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		res.setContents(brmId);
		return Mono.just(res);
	}

	@PostMapping("/brm/delete")
	public Mono<Response> brmDelete(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		String brmId = s(body.get("brmId"));
		int used = sqlSessionTemplate.selectOne("govMeta.countBrmUse", brmId);
		if (used > 0) return Mono.just(fail("하위 분류체계가 있거나 DB·기본값에서 쓰고 있어 지울 수 없습니다."));
		sqlSessionTemplate.delete("govMeta.deleteBrm", brmId);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		return Mono.just(res);
	}

	/**
	 * 정부기능분류체계 CSV 반입 (공공데이터포털 "행정안전부_정부기능별분류체계").
	 * 머리행에서 분류체계ID·분류체계명·상위과제ID·분류체계단계·기관코드·분류체계경로 열을 찾는다. 인코딩은 UTF-8 이 아니면 MS949.
	 */
	@PostMapping("/brm/importCsv")
	public Mono<Response> brmImportCsv(@RequestParam("file") MultipartFile file) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		List<List<String>> rows;
		try {
			rows = parseCsv(decode(file.getBytes()));
		} catch (Exception e) {
			return Mono.just(fail("CSV 를 읽지 못했습니다: " + e.getMessage()));
		}
		if (rows.isEmpty()) return Mono.just(fail("빈 파일입니다."));
		List<String> head = rows.get(0);
		int iId = col(head, "분류체계ID"), iNm = col(head, "분류체계명"), iParent = col(head, "상위과제ID"),
			iLvl = col(head, "분류체계단계"), iInst = col(head, "기관코드"), iPath = col(head, "분류체계경로");
		if (iId < 0 || iNm < 0) return Mono.just(fail("머리행에 '분류체계ID'·'분류체계명' 열이 없습니다."));
		int ok = 0, skip = 0;
		String userId = sessionService.getUserId();
		try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
			for (int r = 1; r < rows.size(); r++) {
				List<String> row = rows.get(r);
				String id = cell(row, iId), nm = cell(row, iNm);
				if (empty(id) || empty(nm)) { skip++; continue; }
				String lvl = cell(row, iLvl);
				Map<String, Object> p = new HashMap<>();
				p.put("brmId", id);
				p.put("brmNm", nm);
				p.put("parentBrmId", empty(cell(row, iParent)) ? null : cell(row, iParent));
				Integer lv = null;
				if (!empty(lvl)) {
					String digits = lvl.replaceAll("[^0-9]", "");
					lv = digits.isEmpty() ? null : Integer.valueOf(digits);
				}
				p.put("brmLvl", lv);
				p.put("brmPath", cell(row, iPath));
				p.put("instCd", cell(row, iInst));
				p.put("brmSrc", "GOV");
				p.put("useYn", "Y");
				p.put("userId", userId);
				session.insert("govMeta.upsertBrm", p);
				ok++;
				if (ok % 1000 == 0) session.flushStatements();
			}
			session.commit();
		}
		log.info(">> brm csv import ok={} skip={} by {}", ok, skip, userId);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		res.setContents("{\"ok\":" + ok + ",\"skip\":" + skip + "}");
		return Mono.just(res);
	}

	private static int col(List<String> head, String nm) {
		for (int i = 0; i < head.size(); i++) if (head.get(i) != null && head.get(i).replace("﻿", "").trim().equals(nm)) return i;
		return -1;
	}

	private static String cell(List<String> row, int i) {
		if (i < 0 || i >= row.size()) return null;
		String v = row.get(i);
		return v == null ? null : v.trim();
	}

	/** UTF-8 로 읽히면 UTF-8(BOM 제거), 아니면 MS949 */
	private static String decode(byte[] b) {
		try {
			String t = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
			return t.startsWith("﻿") ? t.substring(1) : t;
		} catch (CharacterCodingException e) {
			return new String(b, Charset.forName("MS949"));
		}
	}

	/** 큰따옴표 인용을 지키는 CSV 해석 (RFC 4180) */
	private static List<List<String>> parseCsv(String text) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder f = new StringBuilder();
		boolean q = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (q) {
				if (c == '"') {
					if (i + 1 < text.length() && text.charAt(i + 1) == '"') { f.append('"'); i++; }
					else q = false;
				} else f.append(c);
			} else if (c == '"') {
				q = true;
			} else if (c == ',') {
				row.add(f.toString()); f.setLength(0);
			} else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
				row.add(f.toString()); f.setLength(0);
				rows.add(row); row = new ArrayList<>();
			} else f.append(c);
		}
		if (f.length() > 0 || !row.isEmpty()) { row.add(f.toString()); rows.add(row); }
		return rows;
	}

	// ===== 코드 매핑 =====

	@PostMapping("/codeMap/list")
	public Mono<Response> codeMapList(@RequestBody Map<String, Object> filter) throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("govMeta.selectCodeMapList", filter)));
	}

	@PostMapping("/codeMap/save")
	public Mono<Response> codeMapSave(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		String grp = s(body.get("codeGrp"));
		if (!GovMetaService.CODE_GRPS.contains(grp)) return Mono.just(fail("코드 그룹이 올바르지 않습니다."));
		if (empty(s(body.get("localVal"))) || empty(s(body.get("govCode")))) return Mono.just(fail("우리 값과 범정부 코드를 넣어 주세요."));
		Map<String, Object> p = new HashMap<>(body);
		p.put("codeGrp", grp);
		p.put("localVal", s(body.get("localVal")));
		p.put("govCode", s(body.get("govCode")));
		String so = s(body.get("sortOrder"));
		try {
			p.put("sortOrder", empty(so) ? null : Integer.valueOf(so));
		} catch (NumberFormatException e) {
			return Mono.just(fail("순서는 숫자여야 합니다."));
		}
		p.put("userId", sessionService.getUserId());
		sqlSessionTemplate.insert("govMeta.upsertCodeMap", p);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		return Mono.just(res);
	}

	@PostMapping("/codeMap/delete")
	public Mono<Response> codeMapDelete(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		sqlSessionTemplate.delete("govMeta.deleteCodeMap", body);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		return Mono.just(res);
	}

	// ===== 정보시스템·DB =====

	@GetMapping("/sys/list")
	public Mono<Response> sysList() throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("govMeta.selectGovSysList")));
	}

	@PostMapping("/sys/save")
	public Mono<Response> sysSave(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		if (empty(s(body.get("sysCd")))) return Mono.just(fail("시스템을 골라 주세요."));
		for (String k : Arrays.asList("bldYm", "lastUpgrdYm")) {
			String v = s(body.get(k));
			if (!empty(v) && !v.matches("^\\d{6}$")) return Mono.just(fail((k.equals("bldYm") ? "구축연월" : "최근업데이트연월") + "은 YYYYMM 6자리입니다."));
		}
		Map<String, Object> p = new HashMap<>(body);
		p.put("userId", sessionService.getUserId());
		sqlSessionTemplate.insert("govMeta.upsertGovSys", p);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		return Mono.just(res);
	}

	/** 시스템의 DB 목록과 그 DB 를 쓰는 모델 */
	@GetMapping("/db/list")
	public Mono<Response> dbList(@RequestParam("sysCd") String sysCd) throws JsonProcessingException {
		List<Map<String, Object>> dbs = sqlSessionTemplate.selectList("govMeta.selectGovDbList", sysCd);
		List<String> dsIds = new ArrayList<>();
		for (Map<String, Object> d : dbs) dsIds.add(String.valueOf(d.get("dsId")));
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("dbs", dbs);
		if (dsIds.isEmpty()) {
			m.put("models", new ArrayList<>());
		} else {
			Map<String, Object> q = new HashMap<>();
			q.put("dsIds", dsIds);
			m.put("models", sqlSessionTemplate.selectList("govMeta.selectModelsByDs", q));
		}
		return Mono.just(ok(m));
	}

	@PostMapping("/db/save")
	public Mono<Response> dbSave(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		if (empty(s(body.get("dsId")))) return Mono.just(fail("DB 를 골라 주세요."));
		Map<String, Object> p = new HashMap<>(body);
		p.put("userId", sessionService.getUserId());
		sqlSessionTemplate.insert("govMeta.upsertGovDb", p);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		return Mono.just(res);
	}

	// ===== 테이블·컬럼 항목 =====

	@PostMapping("/tbl/list")
	public Mono<Response> tblList(@RequestBody Map<String, Object> filter) throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("govMeta.selectGovTblList", filter)));
	}

	/** 선택 테이블 일괄 적용 — {keys:[{dmId,objOwner,objNm}], govSendYn?, tblTp?, occrCycle?, openTp?, openLimitReason?, openLimitBasis?, openDataLstNm?} */
	@PostMapping("/tbl/apply")
	public Mono<Response> tblApply(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		Object keys = body.get("keys");
		if (!(keys instanceof List) || ((List<?>) keys).isEmpty()) return Mono.just(fail("적용할 테이블을 골라 주세요."));
		String send = s(body.get("govSendYn"));
		if (send != null && !send.equals("Y") && !send.equals("N")) return Mono.just(fail("전송대상은 Y 또는 N 입니다."));
		String openTp = s(body.get("openTp"));
		if (!empty(openTp) && !Arrays.asList("공개", "부분공개", "비공개").contains(openTp)) return Mono.just(fail("공개/비공개여부는 공개·부분공개·비공개 중 하나입니다."));
		boolean any = false;
		for (String k : Arrays.asList("govSendYn", "tblTp", "occrCycle", "openTp", "openLimitReason", "openLimitBasis", "openDataLstNm")) {
			if (body.get(k) != null) any = true;
		}
		if (!any) return Mono.just(fail("적용할 항목이 없습니다."));
		int n = sqlSessionTemplate.update("govMeta.updateGovTbls", body);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		res.setContents(String.valueOf(n));
		return Mono.just(res);
	}

	@PostMapping("/col/list")
	public Mono<Response> colList(@RequestBody Map<String, Object> filter) throws JsonProcessingException {
		if (empty(s(filter.get("dmId")))) return Mono.just(fail("데이터 모델이 없습니다."));
		return Mono.just(ok(sqlSessionTemplate.selectList("govMeta.selectGovColList", filter)));
	}

	/** 선택 컬럼 적용 — {keys:[{dmId,objOwner,objNm,attrNm}], openTp?, openLimitReason?, cnstrDesc?} */
	@PostMapping("/col/apply")
	public Mono<Response> colApply(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		Object keys = body.get("keys");
		if (!(keys instanceof List) || ((List<?>) keys).isEmpty()) return Mono.just(fail("적용할 컬럼을 골라 주세요."));
		String openTp = s(body.get("openTp"));
		if (!empty(openTp) && !Arrays.asList("공개", "비공개").contains(openTp)) return Mono.just(fail("컬럼 공개/비공개여부는 공개·비공개 중 하나입니다."));
		if (body.get("openTp") == null && body.get("openLimitReason") == null && body.get("cnstrDesc") == null) return Mono.just(fail("적용할 항목이 없습니다."));
		int n = sqlSessionTemplate.update("govMeta.updateGovCols", body);
		res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		res.setContents(String.valueOf(n));
		return Mono.just(res);
	}

	/** 테이블 항목 엑셀 내려받기 (우리 양식) — 목록과 같은 필터 */
	@GetMapping("/tbl/download")
	public void tblDownload(@RequestParam Map<String, Object> filter, HttpServletResponse response) throws Exception {
		List<Map<String, Object>> rows = sqlSessionTemplate.selectList("govMeta.selectGovTblList", filter);
		String[] keys = {"dmId", "dmNm", "objOwner", "objNm", "objNmKr", "govSendYn", "tblTp", "occrCycle", "openTp", "openLimitReason", "openLimitBasis", "openDataLstNm"};
		writeXlsx(response, "범정부메타_테이블", TBL_XLS_HEADERS, keys, rows);
	}

	/** 컬럼 항목 엑셀 내려받기 (우리 양식) — 모델(필수)·소유자·테이블 */
	@GetMapping("/col/download")
	public void colDownload(@RequestParam Map<String, Object> filter, HttpServletResponse response) throws Exception {
		List<Map<String, Object>> rows = sqlSessionTemplate.selectList("govMeta.selectGovColList", filter);
		String[] keys = {"dmId", "objOwner", "objNm", "attrNm", "attrNmKr", "dbmsDataType", "openTp", "openLimitReason", "cnstrDesc"};
		writeXlsx(response, "범정부메타_컬럼", COL_XLS_HEADERS, keys, rows);
	}

	private void writeXlsx(HttpServletResponse response, String name, String[] headers, String[] keys, List<Map<String, Object>> rows) throws Exception {
		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sh = wb.createSheet(name);
			Row h = sh.createRow(0);
			for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
			int r = 1;
			for (Map<String, Object> row : rows) {
				Row x = sh.createRow(r++);
				for (int i = 0; i < keys.length; i++) {
					Object v = row.get(keys[i]);
					x.createCell(i).setCellValue(v == null ? "" : String.valueOf(v));
				}
			}
			for (int i = 0; i < headers.length; i++) sh.setColumnWidth(i, 18 * 256);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			wb.write(out);
			response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			response.setHeader("Content-Disposition", "attachment; filename=\""
					+ java.net.URLEncoder.encode(name + ".xlsx", "UTF-8").replace("+", "%20") + "\"");
			response.getOutputStream().write(out.toByteArray());
			response.getOutputStream().flush();
		}
	}

	/** 테이블 항목 엑셀 올리기 — 내려받은 우리 양식. 키(모델 ID·소유자·영문테이블명)로 찾아 항목을 바꾼다 */
	@PostMapping("/tbl/upload")
	public Mono<Response> tblUpload(@RequestParam("file") MultipartFile file) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		List<String> errs = new ArrayList<>();
		int n = 0;
		try (InputStream in = new ByteArrayInputStream(file.getBytes()); Workbook wb = new XSSFWorkbook(in)) {
			Sheet sh = wb.getSheetAt(0);
			if (!headerMatches(sh.getRow(0), TBL_XLS_HEADERS)) return Mono.just(fail("양식이 다릅니다. [엑셀 내려받기] 로 받은 파일을 써 주세요."));
			DataFormatter fmt = new DataFormatter();
			for (int r = 1; r <= sh.getLastRowNum(); r++) {
				Row x = sh.getRow(r);
				if (x == null) continue;
				String dmId = txt(fmt, x, 0), owner = txt(fmt, x, 2), obj = txt(fmt, x, 3);
				if (empty(dmId) || empty(obj)) continue;
				String send = txt(fmt, x, 5), openTp = txt(fmt, x, 8);
				if (!empty(send) && !send.equals("Y") && !send.equals("N")) { errs.add((r + 1) + "행: 전송대상은 Y 또는 N"); continue; }
				if (!empty(openTp) && !Arrays.asList("공개", "부분공개", "비공개").contains(openTp)) { errs.add((r + 1) + "행: 공개/비공개여부는 공개·부분공개·비공개"); continue; }
				Map<String, Object> p = new HashMap<>();
				Map<String, Object> key = new HashMap<>();
				key.put("dmId", dmId);
				key.put("objOwner", owner == null ? "" : owner);
				key.put("objNm", obj);
				p.put("keys", Arrays.asList(key));
				p.put("govSendYn", empty(send) ? "N" : send);
				p.put("tblTp", nz(txt(fmt, x, 6)));
				p.put("occrCycle", nz(txt(fmt, x, 7)));
				p.put("openTp", nz(openTp));
				p.put("openLimitReason", nz(txt(fmt, x, 9)));
				p.put("openLimitBasis", nz(txt(fmt, x, 10)));
				p.put("openDataLstNm", nz(txt(fmt, x, 11)));
				int u = sqlSessionTemplate.update("govMeta.updateGovTbls", p);
				if (u == 0) errs.add((r + 1) + "행: 테이블을 찾을 수 없음 (" + owner + "." + obj + ")");
				n += u;
			}
		} catch (Exception e) {
			return Mono.just(fail("엑셀을 읽지 못했습니다: " + e.getMessage()));
		}
		return Mono.just(uploadResult(n, errs));
	}

	/** 컬럼 항목 엑셀 올리기 — 내려받은 우리 양식 */
	@PostMapping("/col/upload")
	public Mono<Response> colUpload(@RequestParam("file") MultipartFile file) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		List<String> errs = new ArrayList<>();
		int n = 0;
		try (InputStream in = new ByteArrayInputStream(file.getBytes()); Workbook wb = new XSSFWorkbook(in)) {
			Sheet sh = wb.getSheetAt(0);
			if (!headerMatches(sh.getRow(0), COL_XLS_HEADERS)) return Mono.just(fail("양식이 다릅니다. [엑셀 내려받기] 로 받은 파일을 써 주세요."));
			DataFormatter fmt = new DataFormatter();
			for (int r = 1; r <= sh.getLastRowNum(); r++) {
				Row x = sh.getRow(r);
				if (x == null) continue;
				String dmId = txt(fmt, x, 0), owner = txt(fmt, x, 1), obj = txt(fmt, x, 2), attr = txt(fmt, x, 3);
				if (empty(dmId) || empty(obj) || empty(attr)) continue;
				String openTp = txt(fmt, x, 6);
				if (!empty(openTp) && !Arrays.asList("공개", "비공개").contains(openTp)) { errs.add((r + 1) + "행: 컬럼 공개/비공개여부는 공개·비공개"); continue; }
				Map<String, Object> key = new HashMap<>();
				key.put("dmId", dmId);
				key.put("objOwner", owner == null ? "" : owner);
				key.put("objNm", obj);
				key.put("attrNm", attr);
				Map<String, Object> p = new HashMap<>();
				p.put("keys", Arrays.asList(key));
				p.put("openTp", nz(openTp));
				p.put("openLimitReason", nz(txt(fmt, x, 7)));
				p.put("cnstrDesc", nz(txt(fmt, x, 8)));
				int u = sqlSessionTemplate.update("govMeta.updateGovCols", p);
				if (u == 0) errs.add((r + 1) + "행: 컬럼을 찾을 수 없음 (" + obj + "." + attr + ")");
				n += u;
			}
		} catch (Exception e) {
			return Mono.just(fail("엑셀을 읽지 못했습니다: " + e.getMessage()));
		}
		return Mono.just(uploadResult(n, errs));
	}

	private Response uploadResult(int n, List<String> errs) {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("updated", n);
		m.put("errors", errs.size() > 100 ? errs.subList(0, 100) : errs);
		m.put("errorCnt", errs.size());
		try {
			r.setContents(OM.writeValueAsString(m));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
		return r;
	}

	private static boolean headerMatches(Row h, String[] headers) {
		if (h == null) return false;
		DataFormatter fmt = new DataFormatter();
		for (int i = 0; i < headers.length; i++) {
			if (!headers[i].equals(txt(fmt, h, i))) return false;
		}
		return true;
	}

	private static String txt(DataFormatter fmt, Row x, int i) {
		Cell c = x.getCell(i);
		if (c == null) return null;
		String v = fmt.formatCellValue(c);
		return v == null ? null : v.trim();
	}

	/** 엑셀 빈 칸은 "" — updateGovTbls/Cols 가 NULL 로 지운다 */
	private static String nz(String v) {
		return v == null ? "" : v;
	}

	// ===== 생성·전송 =====

	/** 검증 실행 — {sysCd?} 시스템(하위 포함) 또는 {dmId?} 모델. 둘 다 없으면 전송대상 정보시스템 전체 */
	@PostMapping("/send/validate")
	public Mono<Response> validate(@RequestBody Map<String, Object> body) throws JsonProcessingException {
		Response res = denied();
		if (res != null) return Mono.just(res);
		Map<String, Object> r = govMetaService.validate("MANUAL", s(body.get("sysCd")), s(body.get("dmId")), null, sessionService.getUserId());
		return Mono.just(ok(r));
	}

	@PostMapping("/send/list")
	public Mono<Response> sendList(@RequestBody Map<String, Object> filter) throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("govMeta.selectSendList", filter)));
	}

	@GetMapping("/send/errors")
	public Mono<Response> sendErrors(@RequestParam("sendId") String sendId) throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("govMeta.selectSendErrList", sendId)));
	}
}
