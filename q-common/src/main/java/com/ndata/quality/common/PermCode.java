package com.ndata.quality.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 104번 WP2 — 권한 코드.
 *
 * <p>지금까지 관리자(TB_USER.ADM_YN)만 하던 일을 나눈 것이다. 관리자는 모든 권한을 갖고,
 * 관리자가 아닌 사용자는 역할(TB_ROLE)로 이 권한을 받는다. 일반 사용자가 원래 할 수 있던 일에는
 * 권한 코드가 없다.</p>
 *
 * <p>{@link #systemScoped} 인 권한은 시스템(TB_SYS_INFO) 단위로 줄 수 있다 — 시스템별 DB 담당.</p>
 */
public enum PermCode {

	STD_MANAGE("표준 직접 관리", "단어·용어·코드·도메인을 승인 없이 등록·수정·삭제하고, 한글 변환 이력을 전부 본다", false),
	STD_APPROVE("표준 승인", "표준 사전 변경 요청을 승인·반려하고, 모든 사용자의 요청을 본다", false),
	DICT_ADMIN("표준사전 관리", "표준사전 세트를 추가·수정·중지한다", false),
	DM_MANAGE("모델 직접 반영", "데이터 모델 변경을 승인 없이 바로 반영하고, 컬럼 순서와 모델의 시스템·데이터 소스를 바꾼다", false),
	DM_APPROVE("모델 승인", "데이터 모델 변경 신청을 승인·반려하고, 모든 변경 이력을 본다", false),
	DDL_EXEC("DB 반영", "승인된 변경의 DDL 을 대상 DB 에 실행한다", true),
	AREA_ADMIN("영역 관리", "업무영역·주제영역을 등록·수정·삭제한다", false),
	DIAG_TARGET_ADMIN("진단 제외 관리", "테이블·컬럼의 진단 대상 여부를 바꾼다", false),
	SCHEDULE_ADMIN("스케줄 관리", "진단 스케줄을 등록·수정·삭제·실행한다", false),
	BOARD_ADMIN("공지 관리", "공지사항을 쓴다", false),
	USER_ADMIN("사용자·권한 관리", "사용자를 추가·삭제·수정하고 역할과 권한을 관리한다", false),
	DS_ADMIN("데이터 소스 관리", "데이터 소스를 등록·수정·삭제하고 연결을 시험한다", false),
	SYS_ADMIN("시스템 관리", "시스템 트리를 등록·수정·삭제한다", false),
	QUAL_ADMIN("품질 규칙 관리", "데이터 품질 규칙을 삭제하고 카탈로그에서 가져온다", false),
	GOV_META_ADMIN("범정부 메타 관리", "범정부 메타 설정·정보시스템·DB·테이블·컬럼 항목을 관리하고 검증·생성한다", false),
	UDP_ADMIN("사용자 정의 속성 관리", "데이터 모델 테이블의 사용자 정의 속성(관리 항목)을 정의·수정·사용 중지한다", false);

	public final String label;
	public final String desc;
	public final boolean systemScoped;

	PermCode(String label, String desc, boolean systemScoped) {
		this.label = label;
		this.desc = desc;
		this.systemScoped = systemScoped;
	}

	/** 화면의 권한 목록용. */
	public static List<Map<String, Object>> catalog() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (PermCode p : values()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", p.name());
			m.put("label", p.label);
			m.put("desc", p.desc);
			m.put("systemScoped", p.systemScoped);
			list.add(m);
		}
		return list;
	}

	public static boolean isValid(String code) {
		for (PermCode p : values()) {
			if (p.name().equals(code)) return true;
		}
		return false;
	}
}
