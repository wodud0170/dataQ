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
	
    //단어
    public void getWordsExcel(String searchKey, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = new NDQualityRetrieveCond();
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
    public void getTermsListExcel(String searchKey, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = new NDQualityRetrieveCond();
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
        	new String[]{"표준여부","10","CENTER"}
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
    public void getCodeInfoListExcel(String searchKey, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = new NDQualityRetrieveCond();
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
    public void getCodeDataListExcel(String searchKey, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = new NDQualityRetrieveCond();
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
        	new String[]{"코드값설명","70","LEFT"}
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
    public void getDomainsExcel(String searchKey, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        NDQualityRetrieveCond retrieveCond = new NDQualityRetrieveCond();
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
        	new String[]{"표준여부","10","CENTER"}
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

    //데이터모델 테이블정보 다운로드
    public void getDMObjsExcel(String searchKey, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        List<StdDataModelObjVo> dmObjVoLst = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", searchKey);
        long listSize = dmObjVoLst.size();

        // 헤더 {헤더명,너비,정렬기준} 설정
        List<String[]> headers = Arrays.asList(
        	new String[]{"테이블명","20","LEFT"},
        	new String[]{"테이블한글명","30","LEFT"},
        	new String[]{"소유자","20","LEFT"},
        	new String[]{"컬럼개수","20","CENTER"}, 
            new String[]{"테이블설명","30","CENTER"}
        );
        
        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();

        // 헤더 키에 1:1 매칭
        for (int i = 0; i < listSize ; i++) {
        	StdDataModelObjVo dataVo = dmObjVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", dataVo.getObjNm());
            tempMap.put("HEADER2", dataVo.getObjNmKr());
            tempMap.put("HEADER3", dataVo.getObjOwner());
            tempMap.put("HEADER4", dataVo.getObjAttrCnt());
            tempMap.put("HEADER5", dataVo.getObjDesc());
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
    public void getDMAttrsExcel(String searchKey, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 엑셀에 저장할 데이터를 조회
        List<StdDataModelAttrVo> dmAttrVoLst = sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByClctId", searchKey);
        long listSize = dmAttrVoLst.size();

        // 헤더 {헤더명,너비,정렬기준} 설정
        List<String[]> headers = Arrays.asList(
        	new String[]{"테이블명","20","LEFT"},
        	new String[]{"테이블한글명","20","LEFT"},
        	new String[]{"컬럼명","20","LEFT"},
        	new String[]{"컬럼한글명","20","CENTER"}, 
            new String[]{"데이터타입","20","CENTER"}, 
        	new String[]{"데이터길이","15","RIGHT"},
        	new String[]{"데이터소수점길이","20","RIGHT"},
            new String[]{"NULL여부","10","CENTER"},
        	new String[]{"M:3:표준여부:용어","15","CENTER"},//병합헤더 - "병합여부(M):병합개수:병합컬럼명:분할컬럼명"
        	new String[]{"도메인","15","CENTER"},
        	new String[]{"단어","20","LEFT"},
        	new String[]{"PK여부","10","CENTER"},
            new String[]{"FK여부","10","CENTER"},
        	new String[]{"디폴트값","15","CENTER"}
        );
        
        // 데이터 리스트
        List<Map<String, Object>> list = new ArrayList<>();

        // 헤더 키에 1:1 매칭
        for (int i = 0; i < listSize ; i++) {
        	StdDataModelAttrVo dataVo = dmAttrVoLst.get(i);
            Map<String, Object> tempMap = new LinkedHashMap<>();
            tempMap.put("HEADER1", dataVo.getObjNm());
            tempMap.put("HEADER2", dataVo.getObjNmKr());
            tempMap.put("HEADER3", dataVo.getAttrNm());
            tempMap.put("HEADER4", dataVo.getAttrNmKr());
            tempMap.put("HEADER5", dataVo.getDataType());
            tempMap.put("HEADER6", dataVo.getDataLen());
            tempMap.put("HEADER7", dataVo.getDataDecimalLen());
            tempMap.put("HEADER8", dataVo.getNullableYn());
            tempMap.put("HEADER9", dataVo.getTermsStndYn());
            tempMap.put("HEADER10", dataVo.getDomainStndYn());
            StringBuilder sb = new StringBuilder();
            for (int j=0; j < dataVo.getWordLst().length; j++) {
                sb.append(dataVo.getWordLst()[j] + " : " + dataVo.getWordStndLst()[j] + (j < (dataVo.getWordLst().length - 1) ? System.lineSeparator() : ""));
            } 
            tempMap.put("HEADER11", sb.toString());  
            tempMap.put("HEADER12", dataVo.getPkYn());
            tempMap.put("HEADER13", dataVo.getFkYn());
            tempMap.put("HEADER14", dataVo.getDefaultVal());
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

    public static Map<String, Object> setExcelMap(List<String[]> headers, List<Map<String, Object>> dataList, String fileName) {
        Map<String, Object> excelMap = new HashMap<>();
        excelMap.put("headers", headers.stream().map(d -> d[0]).collect(Collectors.toList()));
        excelMap.put("widths", headers.stream().map(d-> d[1]).collect(Collectors.toList()));
        excelMap.put("aligns", headers.stream().map(d-> d[2]).collect(Collectors.toList()));
        excelMap.put("keys", new ArrayList<String>(dataList.stream().findFirst().get().keySet()));
        excelMap.put("list", dataList);
        excelMap.put("fileName", fileName);
        return excelMap;
    }
}
