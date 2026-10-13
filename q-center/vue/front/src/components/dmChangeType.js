/**
 * 데이터 모델 변경 이력(TB_DATA_MODEL_CHANGE_HISTORY.CHANGE_TYPE)의 화면 표시명.
 * 승인 화면·내 변경 신청·변경 이력·신청 모달이 함께 쓴다.
 * PK/FK 4종은 WP0 에서 승인 대상이 됐다 (createPk·deletePk·createFk·deleteFk).
 */
const LABELS = {
  ADD_OBJ: '테이블 추가',
  MODIFY_OBJ: '테이블 수정',
  DEL_OBJ: '테이블 삭제',
  ADD_ATTR: '컬럼 추가',
  MODIFY_ATTR: '컬럼 수정',
  DEL_ATTR: '컬럼 삭제',
  SWAP_ATTR_ORD: '컬럼 순서 변경',
  ADD_PK: 'PK 생성',
  DEL_PK: 'PK 삭제',
  ADD_FK: 'FK 생성',
  DEL_FK: 'FK 삭제',
};

/** 표시명. 목록에 없는 유형은 코드 그대로 보인다. */
export function dmChangeTypeLabel(code) {
  return LABELS[code] || code;
}
