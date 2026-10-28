package com.ndata.quality.service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.util.IOUtils;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityRetrieveCond;
import com.ndata.quality.model.std.StdCodeDataVo;
import com.ndata.quality.model.std.StdCodeInfoVo;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelObjVo;
import com.ndata.quality.model.std.StdDomainVo;
import com.ndata.quality.model.std.StdDomainGroupVo;
import com.ndata.quality.model.std.StdDomainClassificationVo;
import com.ndata.quality.model.std.StdTermsVo;
import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.tool.ExcelExportHandler;

@Slf4j
@Component
public class ExcelDownloadService {

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;
	
	@Autowired
	private ExcelExportHandler excelExportHandler;

	@Autowired
	private StdDictService dictService;

	/** 110번 3.12-6-a — 표준 사전 다운로드는 화면에서 고른 사전만 (비면 기본 사전). 예전에는 사전 없이 조회해 전 사전이 섞였다 */
	private NDQualityRetrieveCond stdCond(String dictId) {
		NDQualityRetrieveCond c = new NDQualityRetrieveCond();
		c.setDictId(StringUtils.isNotEmpty(dictId) ? dictId.trim() : dictService.defaultDictId());
		return c;
	}

    // 104번 WP5-16 — 개인정보 컬럼 (실제 값과 출처)
    public void getPrivColExcel(Map<String, Object> filter, HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<Map<String, Object>> rows = sqlSessionTemplate.selectList("privCol.selectPrivColList", filter);
        List<String[]> headers = Arrays.asList(
            new String[]{"No","6","RIGHT"},
            new String[]{"시스템","20","LEFT"},
            new String[]{"데이터 모델","25","LEFT"},
            new String[]{"소유자","15","LEFT"},
            new String[]{"테이블","25","LEFT"},
            new String[]{"테이블 한글명","25","LEFT"},
            new String[]{"컬럼","25","LEFT"},
            new String[]{"컬럼 한글명","25","LEFT"},
            new String[]{"데이터타입","15","CENTER"},
            new String[]{"용어","20","LEFT"},
            new String[]{"도메인","20","LEFT"},
            new String[]{"개인정보여부","12","CENTER"},
            new String[]{"개인정보여부 출처","15","CENTER"},
            new String[]{"암호화여부","12","CENTER"},
            new String[]{"암호화여부 출처","15","CENTER"}
        );
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("HEADER1",  i + 1);
            m.put("HEADER2",  r.get("sysNm"));
            m.put("HEADER3",  r.get("dmNm"));
            m.put("HEADER4",  r.get("objOwner"));
            m.put("HEADER5",  r.get("objNm"));
            m.put("HEADER6",  r.get("objNmKr"));
            m.put("HEADER7",  r.get("attrNm"));
            m.put("HEADER8",  r.get("attrNmKr"));
            m.put("HEADER9",  r.get("dataType"));
            m.put("HEADER10", r.get("termsNm"));
            m.put("HEADER11", r.get("domainNm"));
            m.put("HEADER12", r.get("privYn"));
            m.put("HEADER13", privSrcNm((String) r.get("privSrc")));
            m.put("HEADER14", r.get("encrYn"));
            m.put("HEADER15", privSrcNm((String) r.get("encrSrc")));
            list.add(m);
        }
        String fileName = "개인정보컬럼_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    /** 104번 WP7-27 — 미사용 표준 목록 (단어·용어·도메인 중 하나, 화면과 같은 필터). WP9-67 — filter 에 runId 가 있으면 예약 실행이 저장한 결과 */
    public void getUnusedStdExcel(String kind, Map<String, Object> filter, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String sqlId = filter.get("runId") != null ? "daStat.selectUnusedResults"
                : "WORD".equals(kind) ? "unusedStd.selectUnusedWords" : "DOMAIN".equals(kind) ? "unusedStd.selectUnusedDomains" : "unusedStd.selectUnusedTerms";
        String kindNm = "WORD".equals(kind) ? "단어" : "DOMAIN".equals(kind) ? "도메인" : "용어";
        List<Map<String, Object>> rows = sqlSessionTemplate.selectList(sqlId, filter);
        List<String[]> headers = Arrays.asList(
            new String[]{"No","6","RIGHT"},
            new String[]{"유형","8","CENTER"},
            new String[]{kindNm + "명","25","LEFT"},
            new String[]{"영문약어명","25","LEFT"},
            new String[]{"WORD".equals(kind) ? "구분" : "DOMAIN".equals(kind) ? "데이터타입" : "도메인","25","LEFT"},
            new String[]{"승인여부","10","CENTER"},
            new String[]{"등록일","12","CENTER"}
        );
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("HEADER1", i + 1);
            m.put("HEADER2", kindNm);
            m.put("HEADER3", r.get("nm"));
            m.put("HEADER4", r.get("engAbrvNm"));
            m.put("HEADER5", r.get("extra"));
            m.put("HEADER6", r.get("aprvYn"));
            m.put("HEADER7", r.get("cretDt"));
            list.add(m);
        }
        String fileName = "미사용표준_" + kindNm + "_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    // 104번 WP9-62·66 — 시스템별 현황 (시스템 트리 순서, 하위 포함 집계)
    public void getDaSystemsExcel(List<Map<String, Object>> rows, HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<String[]> headers = Arrays.asList(
            new String[]{"시스템","40","LEFT"},
            new String[]{"모델 수","8","RIGHT"},
            new String[]{"테이블 수","10","RIGHT"},
            new String[]{"컬럼 수","10","RIGHT"},
            new String[]{"표준 준수율(%)","12","RIGHT"},
            new String[]{"표준 준수율 목표(%)","14","RIGHT"},
            new String[]{"표준 달성","10","CENTER"},
            new String[]{"구조 일치율(%)","12","RIGHT"},
            new String[]{"구조 일치율 목표(%)","14","RIGHT"},
            new String[]{"구조 달성","10","CENTER"},
            new String[]{"추가 컬럼","10","RIGHT"},
            new String[]{"변경 컬럼","10","RIGHT"},
            new String[]{"삭제 컬럼","10","RIGHT"},
            new String[]{"표준 진단 안 한 모델","16","RIGHT"},
            new String[]{"구조 진단 안 한 모델","16","RIGHT"},
            new String[]{"목표 출처","14","LEFT"}
        );
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("HEADER1", r.get("path"));
            m.put("HEADER2", r.get("modelCnt"));
            m.put("HEADER3", r.get("objCnt"));
            m.put("HEADER4", r.get("attrCnt"));
            m.put("HEADER5", r.get("stndRate"));
            m.put("HEADER6", r.get("stndGoal"));
            m.put("HEADER7", achvNm(r.get("stndAchv")));
            m.put("HEADER8", r.get("structRate"));
            m.put("HEADER9", r.get("structGoal"));
            m.put("HEADER10", achvNm(r.get("structAchv")));
            m.put("HEADER11", r.get("addedColumns"));
            m.put("HEADER12", r.get("modifiedColumns"));
            m.put("HEADER13", r.get("deletedColumns"));
            m.put("HEADER14", r.get("undiagStndCnt"));
            m.put("HEADER15", r.get("undiagStructCnt"));
            m.put("HEADER16", r.get("goalSrc"));
            list.add(m);
        }
        String fileName = "시스템별현황_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    private static String achvNm(Object a) {
        return "Y".equals(a) ? "달성" : "N".equals(a) ? "미달" : "";
    }

    private static String privSrcNm(String src) {
        if ("COLUMN".equals(src)) return "컬럼";
        if ("TERMS".equals(src)) return "용어";
        if ("DOMAIN".equals(src)) return "도메인";
        return "기본값";
    }

    // 88번 §16 — 한글 변환 이력 (매핑 정의서)
    public void getTermResolveHistoryExcel(Map<String, Object> filter, HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<Map<String, Object>> rows = sqlSessionTemplate.selectList("termResolve.selectList", filter);
        List<String[]> headers = Arrays.asList(
            new String[]{"No","6","RIGHT"},
            new String[]{"원래 입력 한글 (A)","30","LEFT"},
            new String[]{"표준 매핑 한글 (B)","30","LEFT"},
            new String[]{"표준 매핑 영문 (B)","25","LEFT"},
            new String[]{"한글명 변경","12","CENTER"},
            new String[]{"데이터타입","15","CENTER"},
            new String[]{"길이","8","RIGHT"},
            new String[]{"모델 ID","30","LEFT"},
            new String[]{"오너","15","LEFT"},
            new String[]{"테이블","25","LEFT"},
            new String[]{"컬럼","25","LEFT"},
            new String[]{"변환 사유","40","LEFT"},
            new String[]{"사용자","15","CENTER"},
            new String[]{"변환 시각","20","CENTER"}
        );
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            String input = String.valueOf(r.getOrDefault("inputKrNm", ""));
            String resolvedKr = r.get("resolvedKrNm") == null ? "" : String.valueOf(r.get("resolvedKrNm"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("HEADER1",  i + 1);
            m.put("HEADER2",  input);
            m.put("HEADER3",  resolvedKr);
            m.put("HEADER4",  r.get("resolvedEnNm"));
            m.put("HEADER5",  input.equals(resolvedKr) ? "유지" : "변경");
            m.put("HEADER6",  r.get("resolvedDataType"));
            m.put("HEADER7",  r.get("resolvedDataLen"));
            m.put("HEADER8",  r.get("dmId"));
            m.put("HEADER9",  r.get("objOwner"));
            m.put("HEADER10", r.get("objNm"));
            m.put("HEADER11", r.get("attrNm"));
            m.put("HEADER12", r.get("resolveReason"));
            m.put("HEADER13", r.get("changeUserId"));
            m.put("HEADER14", r.get("changeDt"));
            list.add(m);
        }
        String fileName = "한글변환이력_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    //단어
    public void getWordsExcel(String searchKey, String dictId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = stdCond(dictId);
        retrieveCond.setSchAprvYn("Y");
        if (StringUtils.isNotEmpty(searchKey)) {
            retrieveCond.setSchNm(searchKey);
        }
        List<StdWordVo> stdWordVoLst = sqlSessionTemplate.selectList("word.selectWordList", retrieveCond);
        long listSize = stdWordVoLst.size();

        // 헤더 {헤더명,너비,정렬기준} 설정
        List<String[]> headers = Arrays.asList(
        	new String[]{"No","5","RIGHT"}, 
        	new String[]{"제정차수","10","CENTER"},
        	new String[]{"단어명","30","CENTER"},
        	new String[]{"단어영문약어명","20","LEFT"},
        	new String[]{"단어영문명","30","LEFT"},
        	new String[]{"단어설명","70","LEFT"},
        	new String[]{"형식단어여부","15","CENTER"}, 
        	new String[]{"도메인분류명","15","CENTER"},
        	new String[]{"이음동의어목록","15","CENTER"},
        	new String[]{"금칙어목록","15","CENTER"},
            new String[]{"요청시스템","15","LEFT"},
        	new String[]{"표준여부","10","CENTER"}
        );
        
        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();

        // 헤더 키에 1:1 매칭
        for (int i = 0; i < listSize ; i++) {
        	StdWordVo dataVo = stdWordVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i+1);
            tempMap.put("HEADER2", dataVo.getMagntdOrd());
            tempMap.put("HEADER3", dataVo.getWordNm());
            tempMap.put("HEADER4", dataVo.getWordEngAbrvNm());
            tempMap.put("HEADER5", dataVo.getWordEngNm());
            tempMap.put("HEADER6", dataVo.getWordDesc());
            tempMap.put("HEADER7", dataVo.getWordClsfYn());
            tempMap.put("HEADER8", dataVo.getDomainClsfNm());
            tempMap.put("HEADER9", dataVo.getAllophSynmLst() == null ? null : String.join(",", dataVo.getAllophSynmLst()));//String 배열을 ',' 로 연결한 스트링으로 변환
            tempMap.put("HEADER10", dataVo.getForbdnWordLst() == null ? null : String.join(",", dataVo.getForbdnWordLst()));
            tempMap.put("HEADER11", dataVo.getReqSysNm());
            tempMap.put("HEADER12", dataVo.getCommStndYn());
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        // 파일명 설정
        String fileName = "단어사전_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");

        // ExcelMap 생성
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);

        // 엑셀데이터 생성
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }
    
    //용어
    public void getTermsListExcel(String searchKey, String dictId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = stdCond(dictId);
        retrieveCond.setSchAprvYn("Y");
        if (StringUtils.isNotEmpty(searchKey)) {
            retrieveCond.setSchNm(searchKey);
        }
        List<StdTermsVo> stdTermsVoLst = sqlSessionTemplate.selectList("terms.selectTermsList", retrieveCond);
        long listSize = stdTermsVoLst.size();

        // 헤더 {헤더명,너비,정렬기준} 설정
        List<String[]> headers = Arrays.asList(
        	new String[]{"No","5","RIGHT"}, 
        	new String[]{"제정차수","10","CENTER"},
        	new String[]{"용어명","30","CENTER"},
        	new String[]{"용어설명","70","LEFT"},
        	new String[]{"용어영문약어명","20","LEFT"},
        	new String[]{"도메인명","15","CENTER"}, 
        	new String[]{"허용값","20","LEFT"},
        	new String[]{"저장형식","20","LEFT"},
        	new String[]{"표현형식","20","LEFT"},
        	new String[]{"코드그룹명","15","LEFT"},
        	new String[]{"소관기관명","15","LEFT"},
            new String[]{"이음동의어목록","15","CENTER"},
        	new String[]{"요청시스템","15","LEFT"},
        	new String[]{"표준여부","10","CENTER"},
        	new String[]{"개인정보여부","12","CENTER"},
        	new String[]{"암호화여부","12","CENTER"}
        );
        
        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();

        // 헤더 키에 1:1 매칭
        for (int i = 0; i < listSize ; i++) {
        	StdTermsVo dataVo = stdTermsVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i+1);
            tempMap.put("HEADER2", dataVo.getMagntdOrd());
            tempMap.put("HEADER3", dataVo.getTermsNm());
            tempMap.put("HEADER4", dataVo.getTermsDesc());
            tempMap.put("HEADER5", dataVo.getTermsEngAbrvNm());
            tempMap.put("HEADER6", dataVo.getDomainNm());
            tempMap.put("HEADER7", dataVo.getAllowValLst() == null ? null : String.join(",", dataVo.getAllowValLst()));//String 배열을 ',' 로 연결한 스트링으로 변환
            tempMap.put("HEADER8", dataVo.getStorFmt());
            tempMap.put("HEADER9", dataVo.getExprFmtLst() == null ? null : String.join(" or ", dataVo.getExprFmtLst()));
            tempMap.put("HEADER10", dataVo.getCodeGrp());
            tempMap.put("HEADER11", dataVo.getChrgOrg());
            tempMap.put("HEADER12", dataVo.getAllophSynmLst() == null ? null : String.join(",", dataVo.getAllophSynmLst()));
            tempMap.put("HEADER13", dataVo.getReqSysNm());
            tempMap.put("HEADER14", dataVo.getCommStndYn());
            // 104번 WP5-16 — 용어 값 그대로 (빈칸 = 도메인 값 따름, 다시 올려도 그대로 유지)
            tempMap.put("HEADER15", dataVo.getPrivYn());
            tempMap.put("HEADER16", dataVo.getEncrYn());
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        // 파일명 설정
        String fileName = "용어사전_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");

        // ExcelMap 생성
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);

        // 엑셀데이터 생성
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());    	
    }

    //코드정보
    public void getCodeInfoListExcel(String searchKey, String dictId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = stdCond(dictId);
        retrieveCond.setSchAprvYn("Y");
        if (StringUtils.isNotEmpty(searchKey)) {
            retrieveCond.setSchNm(searchKey);
        }
        List<StdCodeInfoVo> stdCodeInfoVoLst = sqlSessionTemplate.selectList("terms.selectCodeInfoList", retrieveCond);
        long listSize = stdCodeInfoVoLst.size();

        // 헤더 {헤더명,너비,정렬기준} 설정
        List<String[]> headers = Arrays.asList(
        	new String[]{"No","5","RIGHT"}, 
        	new String[]{"제정차수","10","CENTER"},
        	new String[]{"코드명","30","CENTER"},
        	new String[]{"코드설명","70","LEFT"},
        	new String[]{"코드영문명","20","LEFT"},
        	new String[]{"도메인명","15","CENTER"}, 
        	new String[]{"허용값","20","LEFT"},
        	new String[]{"저장형식","20","LEFT"},
        	new String[]{"표현형식","20","LEFT"},
        	new String[]{"코드그룹명","15","LEFT"},
        	new String[]{"소관기관명","15","LEFT"},
            new String[]{"이음동의어목록","15","CENTER"},
        	new String[]{"요청시스템","15","LEFT"},
        	new String[]{"표준여부","10","CENTER"}
        );
        
        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();

        // 헤더 키에 1:1 매칭
        for (int i = 0; i < listSize ; i++) {
        	StdCodeInfoVo dataVo = stdCodeInfoVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i+1);
            tempMap.put("HEADER2", dataVo.getMagntdOrd());
            tempMap.put("HEADER3", dataVo.getCodeNm());
            tempMap.put("HEADER4", dataVo.getCodeDesc());
            tempMap.put("HEADER5", dataVo.getCodeEngAbrvNm());
            tempMap.put("HEADER6", dataVo.getDomainNm());
            tempMap.put("HEADER7", dataVo.getAllowValLst() == null ? null : String.join(",", dataVo.getAllowValLst()));//String 배열을 ',' 로 연결한 스트링으로 변환
            tempMap.put("HEADER8", dataVo.getStorFmt());
            tempMap.put("HEADER9", dataVo.getExprFmtLst() == null ? null : String.join(" or ", dataVo.getExprFmtLst()));
            tempMap.put("HEADER10", dataVo.getCodeGrp());
            tempMap.put("HEADER11", dataVo.getChrgOrg());
            tempMap.put("HEADER12", dataVo.getAllophSynmLst() == null ? null : String.join(",", dataVo.getAllophSynmLst()));
            tempMap.put("HEADER13", dataVo.getReqSysNm());
            tempMap.put("HEADER14", dataVo.getCommStndYn());
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        // 파일명 설정
        String fileName = "코드정보_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");

        // ExcelMap 생성
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);

        // 엑셀데이터 생성
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());    	
    }

    //코드데이터(항목값)
    public void getCodeDataListExcel(String searchKey, String dictId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = stdCond(dictId);
        retrieveCond.setSchAprvYn("Y");
        if (StringUtils.isNotEmpty(searchKey)) {
            retrieveCond.setSchNm(searchKey);
        }
        List<StdCodeDataVo> stdCodeDataVoLst = sqlSessionTemplate.selectList("codedata.selectCodeDataList", retrieveCond);
        long listSize = stdCodeDataVoLst.size();

        // 헤더 {헤더명,너비,정렬기준} 설정
        List<String[]> headers = Arrays.asList(
        	new String[]{"No","5","RIGHT"}, 
        	new String[]{"코드그룹","15","CENTER"},
        	new String[]{"코드명","30","CENTER"},
        	new String[]{"코드영문명","20","LEFT"},
        	new String[]{"코드값","15","CENTER"}, 
        	new String[]{"코드값설명","70","LEFT"},
        	new String[]{"상위코드값","15","CENTER"}
        );
        
        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();

        // 헤더 키에 1:1 매칭
        for (int i = 0; i < listSize ; i++) {
        	StdCodeDataVo dataVo = stdCodeDataVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i+1);
            tempMap.put("HEADER2", dataVo.getCodeGrp());
            tempMap.put("HEADER3", dataVo.getCodeNm());
            tempMap.put("HEADER4", dataVo.getCodeEngNm());
            tempMap.put("HEADER5", dataVo.getCodeVal());
            tempMap.put("HEADER6", dataVo.getCodeValDesc());
            tempMap.put("HEADER7", dataVo.getParentCodeVal());   // 104번 WP6-3 — 계층 코드의 상위 코드값
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        // 파일명 설정
        String fileName = "코드데이터_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");

        // ExcelMap 생성
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);

        // 엑셀데이터 생성
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());    	
    }

    //도메인
    public void getDomainsExcel(String searchKey, String dictId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = stdCond(dictId);
        retrieveCond.setSchAprvYn("Y");
        if (StringUtils.isNotEmpty(searchKey)) {
            retrieveCond.setSchNm(searchKey);
        }
        List<StdDomainVo> stdDomainVoLst = sqlSessionTemplate.selectList("domain.selectDomainList", retrieveCond);
        long listSize = stdDomainVoLst.size();

        // 헤더 {헤더명,너비,정렬기준} 설정
        List<String[]> headers = Arrays.asList(
        	new String[]{"No","5","RIGHT"}, 
        	new String[]{"제정차수","10","CENTER"},
        	new String[]{"도메인그룹명","20","LEFT"},
        	new String[]{"도메인분류명","20","LEFT"},
        	new String[]{"도메인명","20","LEFT"},
        	new String[]{"도메인설명","70","LEFT"},
        	new String[]{"데이터타입","15","CENTER"}, 
        	new String[]{"데이터길이","15","RIGHT"},
        	new String[]{"데이터소수점길이","20","CENTER"},
        	new String[]{"저장형식","20","LEFT"},
        	new String[]{"표현형식","20","LEFT"},
        	new String[]{"단위","15","LEFT"},
        	new String[]{"허용값","30","LEFT"},
            new String[]{"요청시스템","15","LEFT"},
        	new String[]{"표준여부","10","CENTER"},
        	new String[]{"개인정보여부","12","CENTER"},
        	new String[]{"암호화여부","12","CENTER"}
        );
        
        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();

        // 헤더 키에 1:1 매칭
        for (int i = 0; i < listSize ; i++) {
        	StdDomainVo dataVo = stdDomainVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i+1);
            tempMap.put("HEADER2", dataVo.getMagntdOrd());
            tempMap.put("HEADER3", dataVo.getDomainGrpNm());
            tempMap.put("HEADER4", dataVo.getDomainClsfNm());
            tempMap.put("HEADER5", dataVo.getDomainNm());
            tempMap.put("HEADER6", dataVo.getDomainDesc());
            tempMap.put("HEADER7", dataVo.getDataType());
            tempMap.put("HEADER8", dataVo.getDataLen());
            tempMap.put("HEADER9", dataVo.getDataDecimalLen());
            tempMap.put("HEADER10", dataVo.getStorFmt());
            tempMap.put("HEADER11", dataVo.getExprFmtLst() == null ? null : String.join("\\n", dataVo.getExprFmtLst()));//String 배열을 '\\n' 로 연결한 스트링으로 변환
            tempMap.put("HEADER12", dataVo.getDataUnit());
            tempMap.put("HEADER13", dataVo.getAllowValLst() == null ? null : String.join("\\n", dataVo.getAllowValLst()));//String 배열을 '\\n' 로 연결한 스트링으로 변환
            tempMap.put("HEADER14", dataVo.getReqSysNm());
            tempMap.put("HEADER15", dataVo.getCommStndYn());
            tempMap.put("HEADER16", dataVo.getPrivYn());
            tempMap.put("HEADER17", dataVo.getEncrYn());
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        // 파일명 설정
        String fileName = "도메인_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");

        // ExcelMap 생성
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);

        // 엑셀데이터 생성
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    //도메인 그룹
    public void getDomainGroupsExcel(String searchKey, String dictId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        NDQualityRetrieveCond retrieveCond = stdCond(dictId);
        retrieveCond.setSchAprvYn("Y");
        if (StringUtils.isNotEmpty(searchKey)) {
            retrieveCond.setSchNm(searchKey);
        }
        List<StdDomainGroupVo> voLst = sqlSessionTemplate.selectList("domain.selectDomainGroupList", retrieveCond);
        long listSize = voLst.size();

        List<String[]> headers = Arrays.asList(
            new String[]{"No","5","RIGHT"},
            new String[]{"도메인그룹명","30","LEFT"},
            new String[]{"표준여부","10","CENTER"}
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            StdDomainGroupVo dataVo = voLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i+1);
            tempMap.put("HEADER2", dataVo.getDomainGrpNm());
            tempMap.put("HEADER3", dataVo.getCommStndYn());
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        String fileName = "도메인그룹_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    //도메인 분류
    public void getDomainClsfsExcel(String searchKey, String dictId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        NDQualityRetrieveCond retrieveCond = stdCond(dictId);
        retrieveCond.setSchAprvYn("Y");
        if (StringUtils.isNotEmpty(searchKey)) {
            retrieveCond.setSchNm(searchKey);
        }
        List<StdDomainClassificationVo> voLst = sqlSessionTemplate.selectList("domain.selectDomainClassificationList", retrieveCond);
        long listSize = voLst.size();

        List<String[]> headers = Arrays.asList(
            new String[]{"No","5","RIGHT"},
            new String[]{"도메인그룹명","30","LEFT"},
            new String[]{"도메인분류명","30","LEFT"},
            new String[]{"표준여부","10","CENTER"}
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            StdDomainClassificationVo dataVo = voLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i+1);
            tempMap.put("HEADER2", dataVo.getDomainGrpNm());
            tempMap.put("HEADER3", dataVo.getDomainClsfNm());
            tempMap.put("HEADER4", dataVo.getCommStndYn());
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        String fileName = "도메인분류_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    //데이터모델 테이블정보 다운로드
    public void getDMObjsExcel(String searchKey, String viewerId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회 — 110 4.12-11: 화면 목록과 같이 승인됐거나 내가 요청한 행만 (DataModelController.visibleTo)
        List<StdDataModelObjVo> dmObjVoLst = new ArrayList<>();
        for (StdDataModelObjVo o : sqlSessionTemplate.<StdDataModelObjVo>selectList("datamodel.selectDataModelObjListByClctId", searchKey)) {
            if (dmVisible(o.getAprvStatus(), o.getRequesterUserId(), viewerId)) dmObjVoLst.add(o);
        }
        long listSize = dmObjVoLst.size();

        // 86번 #11 — 업로드 양식 (DataModelController.TABLE_HEADERS) 와 동일 순서·명칭. 다운로드 → 재업로드 가능.
        // 110 4.12-22 — 업로드 양식의 "업무영역"·"테이블스페이스" 열도 (예전에는 빠져 다시 올리면 비었다)
        List<String[]> headers = new ArrayList<>(Arrays.asList(
            new String[]{"소유자","20","LEFT"},
            new String[]{"업무영역","20","LEFT"},
            new String[]{"테이블명(한글)","30","LEFT"},
            new String[]{"테이블명(영문)","25","LEFT"},
            new String[]{"테이블스페이스","20","LEFT"},
            new String[]{"설명","40","LEFT"}
        ));
        // 104번 WP8-31 — 사용 중인 사용자 정의 속성 열 (업로드 양식과 같은 이름이라 다시 올릴 수 있다)
        Map<String, Object> udpP = new HashMap<>();
        udpP.put("useOnly", "Y");
        List<Map<String, Object>> udpDefs = sqlSessionTemplate.selectList("udp.selectDefs", udpP);
        for (Map<String, Object> d : udpDefs) headers.add(new String[]{(String) d.get("udpNm"), "20", "LEFT"});
        Map<String, String> udpVals = new HashMap<>();   // owner|obj|udpId → 값
        for (Map<String, Object> v : sqlSessionTemplate.<Map<String, Object>>selectList("udp.selectModelVals", searchKey)) {
            udpVals.put(v.get("objOwner") + "|" + v.get("objNm") + "|" + v.get("udpId"), (String) v.get("val"));
        }

        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < listSize ; i++) {
            StdDataModelObjVo dataVo = dmObjVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", dataVo.getObjOwner());
            tempMap.put("HEADER2", dataVo.getBizAreaNm());
            tempMap.put("HEADER3", dataVo.getObjNmKr());
            tempMap.put("HEADER4", dataVo.getObjNm());
            tempMap.put("HEADER5", dataVo.getTablespaceNm());
            tempMap.put("HEADER6", dataVo.getObjDesc());
            int h = 7;
            for (Map<String, Object> d : udpDefs) {
                tempMap.put("HEADER" + (h++), udpVals.get((dataVo.getObjOwner() == null ? "" : dataVo.getObjOwner()) + "|" + dataVo.getObjNm() + "|" + d.get("udpId")));
            }
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        // 파일명 설정
        String fileName = "데이터모델_테이블정보_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");

        // ExcelMap 생성
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);

        // 엑셀데이터 생성
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    //데이터모델 컬럼정보 다운로드
    public void getDMAttrsExcel(String searchKey, String viewerId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회 — 110 4.12-11: 화면 목록과 같이 승인됐거나 내가 요청한 행만
        List<StdDataModelAttrVo> all = sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByClctId", searchKey);
        List<StdDataModelAttrVo> dmAttrVoLst = new ArrayList<>();
        // 110 4.12-22 — "참조 테이블(한글)"·"참조 컬럼(한글)" 은 업로드가 한글명으로 찾으므로 한글명을 쓴다 (예전에는 물리명)
        Map<String, String> objKr = new HashMap<>();    // 소유자|테이블 → 테이블 한글명
        Map<String, String> attrKr = new HashMap<>();   // 소유자|테이블|컬럼 → 컬럼 한글명
        for (StdDataModelAttrVo a : all) {
            if (dmVisible(a.getAprvStatus(), a.getRequesterUserId(), viewerId)) dmAttrVoLst.add(a);
            objKr.put(a.getObjOwner() + "|" + a.getObjNm(), a.getObjNmKr());
            attrKr.put(a.getObjOwner() + "|" + a.getObjNm() + "|" + a.getAttrNm(), a.getAttrNmKr());
        }
        long listSize = dmAttrVoLst.size();

        // 86번 #11 — 업로드 양식 (DataModelController.ATTR_HEADERS) 와 동일 순서·명칭 (다운로드 → 재업로드 가능).
        // 표준여부/용어/도메인/단어 같은 derived 컬럼은 백업 대상 아님 (재업로드 시 표준화 단계에서 다시 생성).
        List<String[]> headers = Arrays.asList(
            new String[]{"소유자","15","LEFT"},
            new String[]{"테이블명(영문)","25","LEFT"},
            new String[]{"테이블명(한글)","25","LEFT"},
            new String[]{"컬럼명(영문)","25","LEFT"},
            new String[]{"컬럼명(한글)","25","LEFT"},
            new String[]{"데이터타입","15","CENTER"},
            new String[]{"길이","10","RIGHT"},
            new String[]{"소수점자리","12","RIGHT"},
            new String[]{"컬럼 순서","10","RIGHT"},
            new String[]{"NULL여부","10","CENTER"},
            new String[]{"PK여부","10","CENTER"},
            new String[]{"FK여부","10","CENTER"},
            new String[]{"디폴트값","20","LEFT"},
            new String[]{"참조 테이블(한글)","25","LEFT"},
            new String[]{"참조 컬럼(한글)","25","LEFT"},
            new String[]{"삭제 규칙","12","CENTER"}
        );

        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < listSize ; i++) {
            StdDataModelAttrVo dataVo = dmAttrVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1",  dataVo.getObjOwner());
            tempMap.put("HEADER2",  dataVo.getObjNm());
            tempMap.put("HEADER3",  dataVo.getObjNmKr());
            tempMap.put("HEADER4",  dataVo.getAttrNm());
            tempMap.put("HEADER5",  dataVo.getAttrNmKr());
            tempMap.put("HEADER6",  dataVo.getDataType());
            tempMap.put("HEADER7",  dataVo.getDataLen());
            tempMap.put("HEADER8",  dataVo.getDataDecimalLen());
            tempMap.put("HEADER9",  dataVo.getAttrOrder());
            tempMap.put("HEADER10", dataVo.getNullableYn());
            tempMap.put("HEADER11", dataVo.getPkYn());
            tempMap.put("HEADER12", dataVo.getFkYn());
            tempMap.put("HEADER13", dataVo.getDefaultVal());
            String refOwner = dataVo.getFkParentObjOwner() == null ? dataVo.getObjOwner() : dataVo.getFkParentObjOwner();
            tempMap.put("HEADER14", dataVo.getFkParentObjNm() == null ? null : objKr.get(refOwner + "|" + dataVo.getFkParentObjNm()));
            tempMap.put("HEADER15", dataVo.getFkParentAttrNm() == null ? null
                    : attrKr.get(refOwner + "|" + dataVo.getFkParentObjNm() + "|" + dataVo.getFkParentAttrNm()));
            tempMap.put("HEADER16", "");  // 삭제 규칙 — VO 에 미보존 (CONSTRAINT 테이블 별도). 백업 시 공란
            list.add(tempMap);
        }
        log.info(">> excel export size={}", list.size());

        // 파일명 설정
        String fileName = "데이터모델_컬럼정보_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");

        // ExcelMap 생성
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);

        // 엑셀데이터 생성
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    /** 88번 거버넌스 — 화면에 보이는 모델 행 (q-center DataModelController.visibleTo 와 같은 기준): 승인됐거나(상태 없음 포함) 본인이 요청한 행 */
    private static boolean dmVisible(String aprvStatus, String requesterUserId, String userId) {
        return aprvStatus == null || "APPROVED".equals(aprvStatus) || (userId != null && userId.equals(requesterUserId));
    }

    //구조진단 결과 다운로드
    public void getStructDiagResultExcel(String diagId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<Map<String, Object>> detailList = sqlSessionTemplate.selectList("structdiag.selectStructDiagDetailList", diagId);
        long listSize = detailList.size();

        List<String[]> headers = Arrays.asList(
            new String[]{"No","5","RIGHT"},
            new String[]{"오너","15","LEFT"},
            new String[]{"테이블명","25","LEFT"},
            new String[]{"컬럼명","25","LEFT"},
            new String[]{"변경유형","12","CENTER"},
            new String[]{"이전 타입","15","CENTER"},
            new String[]{"이전 길이","10","RIGHT"},
            new String[]{"이전 Nullable","12","CENTER"},
            new String[]{"현재 타입","15","CENTER"},
            new String[]{"현재 길이","10","RIGHT"},
            new String[]{"현재 Nullable","12","CENTER"}
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            Map<String, Object> d = detailList.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i + 1);
            tempMap.put("HEADER2", d.get("owner"));
            tempMap.put("HEADER3", d.get("tableNm"));
            tempMap.put("HEADER4", d.get("columnNm"));
            tempMap.put("HEADER5", d.get("changeType"));
            tempMap.put("HEADER6", d.get("prevDataType"));
            tempMap.put("HEADER7", d.get("prevDataLen"));
            tempMap.put("HEADER8", d.get("prevNullable"));
            tempMap.put("HEADER9", d.get("currDataType"));
            tempMap.put("HEADER10", d.get("currDataLen"));
            tempMap.put("HEADER11", d.get("currNullable"));
            list.add(tempMap);
        }
        log.info(">> struct diag excel export size={}", list.size());

        String fileName = "구조진단결과_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    //구조진단 인덱스 변경 결과 다운로드
    public void getStructDiagIndexResultExcel(String diagId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<Map<String, Object>> detailList = sqlSessionTemplate.selectList("structdiag.selectStructDiagIndexDetailList", diagId);
        long listSize = detailList.size();

        List<String[]> headers = Arrays.asList(
            new String[]{"No","5","RIGHT"},
            new String[]{"오너","15","LEFT"},
            new String[]{"테이블명","25","LEFT"},
            new String[]{"인덱스명","25","LEFT"},
            new String[]{"변경유형","12","CENTER"},
            new String[]{"이전 타입","15","CENTER"},
            new String[]{"이전 유니크","12","CENTER"},
            new String[]{"이전 구성컬럼","30","LEFT"},
            new String[]{"현재 타입","15","CENTER"},
            new String[]{"현재 유니크","12","CENTER"},
            new String[]{"현재 구성컬럼","30","LEFT"}
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            Map<String, Object> d = detailList.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i + 1);
            tempMap.put("HEADER2", d.get("owner"));
            tempMap.put("HEADER3", d.get("tableNm"));
            tempMap.put("HEADER4", d.get("indexNm"));
            tempMap.put("HEADER5", d.get("changeType"));
            tempMap.put("HEADER6", d.get("prevIndexType"));
            tempMap.put("HEADER7", d.get("prevUniqueness"));
            tempMap.put("HEADER8", d.get("prevColumns"));
            tempMap.put("HEADER9", d.get("currIndexType"));
            tempMap.put("HEADER10", d.get("currUniqueness"));
            tempMap.put("HEADER11", d.get("currColumns"));
            list.add(tempMap);
        }
        log.info(">> struct diag index excel export size={}", list.size());

        String fileName = "구조진단결과_인덱스_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    //구조진단 제약조건 변경 결과 다운로드
    public void getStructDiagConstraintResultExcel(String diagId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<Map<String, Object>> detailList = sqlSessionTemplate.selectList("structdiag.selectStructDiagConstraintDetailList", diagId);
        long listSize = detailList.size();

        List<String[]> headers = Arrays.asList(
            new String[]{"No","5","RIGHT"},
            new String[]{"오너","15","LEFT"},
            new String[]{"테이블명","25","LEFT"},
            new String[]{"제약조건명","25","LEFT"},
            new String[]{"변경유형","12","CENTER"},
            new String[]{"이전 유형","10","CENTER"},
            new String[]{"이전 구성컬럼","25","LEFT"},
            new String[]{"이전 참조테이블","20","LEFT"},
            new String[]{"이전 참조컬럼","20","LEFT"},
            new String[]{"현재 유형","10","CENTER"},
            new String[]{"현재 구성컬럼","25","LEFT"},
            new String[]{"현재 참조테이블","20","LEFT"},
            new String[]{"현재 참조컬럼","20","LEFT"}
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            Map<String, Object> d = detailList.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", i + 1);
            tempMap.put("HEADER2", d.get("owner"));
            tempMap.put("HEADER3", d.get("tableNm"));
            tempMap.put("HEADER4", d.get("constraintNm"));
            tempMap.put("HEADER5", d.get("changeType"));
            tempMap.put("HEADER6", d.get("prevConstraintType"));
            tempMap.put("HEADER7", d.get("prevColumns"));
            tempMap.put("HEADER8", d.get("prevRefTable"));
            tempMap.put("HEADER9", d.get("prevRefColumns"));
            tempMap.put("HEADER10", d.get("currConstraintType"));
            tempMap.put("HEADER11", d.get("currColumns"));
            tempMap.put("HEADER12", d.get("currRefTable"));
            tempMap.put("HEADER13", d.get("currRefColumns"));
            list.add(tempMap);
        }
        log.info(">> struct diag constraint excel export size={}", list.size());

        String fileName = "구조진단결과_제약조건_" + StringUtils.getTimeString(System.currentTimeMillis(), "yyyyMMddHHmmss");
        Map<String, Object> excelMap = setExcelMap(headers, list, fileName);
        ByteArrayInputStream stream = excelExportHandler.buildExcelDocument(excelMap, request, response);
        IOUtils.copy(stream, response.getOutputStream());
    }

    public static Map<String, Object> setExcelMap(List<String[]> headers, List<Map<String, Object>> dataList, String fileName) {
        Map<String, Object> excelMap = new HashMap<>();
        excelMap.put("headers", headers.stream().map(d -> d[0]).collect(Collectors.toList()));
        excelMap.put("widths", headers.stream().map(d-> d[1]).collect(Collectors.toList()));
        excelMap.put("aligns", headers.stream().map(d-> d[2]).collect(Collectors.toList()));
        // 86번 #33 — dataList 가 비어있으면 findFirst().get() 이 NoSuchElementException 던짐.
        //   keys 가 비어있으면 헤더만 있는 빈 엑셀이 만들어지지만 안전.
        List<String> keys = dataList.isEmpty()
                ? headers.stream().map(d -> "HEADER" + (headers.indexOf(d) + 1)).collect(Collectors.toList())
                : new ArrayList<String>(dataList.get(0).keySet());
        excelMap.put("keys", keys);
        excelMap.put("list", dataList);
        excelMap.put("fileName", fileName);
        return excelMap;
    }
}
