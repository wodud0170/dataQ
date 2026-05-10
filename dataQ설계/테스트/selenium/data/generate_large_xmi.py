# 55 테이블, 4 schema, 한글명 alias (ownedComment) 포함 XMI 생성
import os

# 4 schema, 총 55 테이블
schemas = {
    'HR': [
        ('EMPLOYEE', '직원', [('EMP_ID','직원ID','Integer',True),('EMP_NAME','직원명','String',False),('EMAIL','이메일','String',False),('HIRE_DT','입사일자','Date',False),('DEPT_ID','부서ID','fk-DEPARTMENT',False),('SALARY','급여','Decimal',False)]),
        ('DEPARTMENT', '부서', [('DEPT_ID','부서ID','Integer',True),('DEPT_NAME','부서명','String',False),('MANAGER_ID','관리자ID','Integer',False)]),
        ('POSITION', '직급', [('POS_ID','직급ID','Integer',True),('POS_NAME','직급명','String',False),('LEVEL_CD','레벨코드','String',False)]),
        ('ATTENDANCE', '근태', [('ATT_ID','근태ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('CHECK_IN_DT','출근일시','DateTime',False),('CHECK_OUT_DT','퇴근일시','DateTime',False)]),
        ('LEAVE_REQUEST', '휴가신청', [('REQ_ID','신청ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('LEAVE_TYPE','휴가유형','String',False),('START_DT','시작일자','Date',False),('END_DT','종료일자','Date',False),('STATUS_CD','상태코드','String',False)]),
        ('PAYROLL', '급여대장', [('PAYROLL_ID','급여ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('PAY_DT','지급일자','Date',False),('AMT','금액','Decimal',False)]),
        ('TRAINING', '교육', [('TR_ID','교육ID','Integer',True),('TR_NAME','교육명','String',False),('TR_DESC','교육설명','String',False)]),
        ('EMP_TRAINING', '직원교육', [('ETR_ID','직원교육ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('TR_ID','교육ID','fk-TRAINING',False),('COMP_DT','이수일자','Date',False)]),
        ('PERFORMANCE_REVIEW', '성과평가', [('PRV_ID','평가ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('REVIEW_DT','평가일자','Date',False),('SCORE','점수','Integer',False)]),
        ('JOB_HISTORY', '직무이력', [('JH_ID','이력ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('POS_ID','직급ID','fk-POSITION',False),('START_DT','시작일자','Date',False),('END_DT','종료일자','Date',False)]),
        ('BENEFIT', '복리후생', [('BEN_ID','복리후생ID','Integer',True),('BEN_NAME','복리후생명','String',False),('BEN_DESC','복리후생설명','String',False)]),
        ('EMP_BENEFIT', '직원복리후생', [('EBN_ID','직원복리후생ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('BEN_ID','복리후생ID','fk-BENEFIT',False),('GRANT_DT','지급일자','Date',False)]),
        ('OVERTIME', '초과근무', [('OT_ID','초과근무ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('OT_DT','일자','Date',False),('HOURS','시간','Decimal',False)]),
        ('CERTIFICATE', '자격증', [('CERT_ID','자격증ID','Integer',True),('EMP_ID','직원ID','fk-EMPLOYEE',False),('CERT_NAME','자격증명','String',False),('ISSUE_DT','발급일자','Date',False),('EXPIRE_DT','만료일자','Date',False)]),
        ('ORG_UNIT', '조직단위', [('ORG_ID','조직ID','Integer',True),('ORG_NAME','조직명','String',False),('PARENT_ORG_ID','상위조직ID','Integer',False)]),
    ],
    'SALES': [
        ('CUSTOMER', '고객', [('CUST_ID','고객ID','Integer',True),('CUST_NAME','고객명','String',False),('PHONE','전화번호','String',False),('EMAIL','이메일','String',False),('REG_DT','등록일자','Date',False)]),
        ('SALES_ORDER', '판매주문', [('ORD_ID','주문ID','Integer',True),('CUST_ID','고객ID','fk-CUSTOMER',False),('ORD_DT','주문일시','DateTime',False),('TOTAL_AMT','총금액','Decimal',False),('STATUS_CD','상태코드','String',False)]),
        ('ORDER_ITEM', '주문항목', [('OITM_ID','주문항목ID','Integer',True),('ORD_ID','주문ID','fk-SALES_ORDER',False),('PROD_ID','상품ID','Integer',False),('QTY','수량','Integer',False),('UNIT_PRICE','단가','Decimal',False)]),
        ('PROMOTION', '프로모션', [('PROMO_ID','프로모션ID','Integer',True),('PROMO_NAME','프로모션명','String',False),('START_DT','시작일자','Date',False),('END_DT','종료일자','Date',False),('DISCOUNT_RATE','할인율','Decimal',False)]),
        ('CAMPAIGN', '캠페인', [('CAMP_ID','캠페인ID','Integer',True),('CAMP_NAME','캠페인명','String',False),('TARGET_SEGMENT','타겟세그먼트','String',False)]),
        ('CUSTOMER_GRADE', '고객등급', [('GRADE_CD','등급코드','String',True),('GRADE_NAME','등급명','String',False),('MIN_AMT','최소금액','Decimal',False)]),
        ('SHIPMENT', '배송', [('SHIP_ID','배송ID','Integer',True),('ORD_ID','주문ID','fk-SALES_ORDER',False),('SHIP_DT','배송일시','DateTime',False),('TRACKING_NO','송장번호','String',False)]),
        ('RETURN_REQUEST', '반품신청', [('RET_ID','반품ID','Integer',True),('ORD_ID','주문ID','fk-SALES_ORDER',False),('REASON','사유','String',False),('REQ_DT','신청일자','Date',False)]),
        ('REFUND', '환불', [('REF_ID','환불ID','Integer',True),('RET_ID','반품ID','fk-RETURN_REQUEST',False),('AMT','금액','Decimal',False),('REFUND_DT','환불일자','Date',False)]),
        ('SALES_REGION', '판매지역', [('REGION_CD','지역코드','String',True),('REGION_NAME','지역명','String',False)]),
        ('SALES_REP', '영업담당자', [('REP_ID','담당자ID','Integer',True),('REP_NAME','담당자명','String',False),('REGION_CD','지역코드','fk-SALES_REGION',False)]),
        ('QUOTATION', '견적', [('QT_ID','견적ID','Integer',True),('CUST_ID','고객ID','fk-CUSTOMER',False),('QT_DT','견적일자','Date',False),('VALID_UNTIL','유효기한','Date',False)]),
        ('CONTRACT', '계약', [('CTR_ID','계약ID','Integer',True),('CUST_ID','고객ID','fk-CUSTOMER',False),('SIGN_DT','서명일자','Date',False),('AMT','계약금액','Decimal',False)]),
        ('LEAD', '잠재고객', [('LEAD_ID','잠재고객ID','Integer',True),('LEAD_NAME','잠재고객명','String',False),('SOURCE_CD','소스코드','String',False),('STATUS_CD','상태코드','String',False)]),
        ('OPPORTUNITY', '영업기회', [('OPP_ID','기회ID','Integer',True),('LEAD_ID','잠재고객ID','fk-LEAD',False),('STAGE','단계','String',False),('CLOSE_DT','종료일자','Date',False)]),
    ],
    'INVENTORY': [
        ('PRODUCT', '상품', [('PROD_ID','상품ID','Integer',True),('PROD_NAME','상품명','String',False),('CATEGORY_CD','분류코드','String',False),('UNIT_PRICE','단가','Decimal',False)]),
        ('CATEGORY', '상품분류', [('CATEGORY_CD','분류코드','String',True),('CATEGORY_NAME','분류명','String',False),('PARENT_CD','상위분류코드','String',False)]),
        ('WAREHOUSE', '창고', [('WH_ID','창고ID','Integer',True),('WH_NAME','창고명','String',False),('LOC_ADDR','위치주소','String',False)]),
        ('STOCK', '재고', [('STK_ID','재고ID','Integer',True),('PROD_ID','상품ID','fk-PRODUCT',False),('WH_ID','창고ID','fk-WAREHOUSE',False),('QTY','수량','Integer',False)]),
        ('STOCK_MOVEMENT', '재고이동', [('SM_ID','이동ID','Integer',True),('STK_ID','재고ID','fk-STOCK',False),('MOVE_TYPE','이동유형','String',False),('QTY','수량','Integer',False),('MOVE_DT','이동일시','DateTime',False)]),
        ('SUPPLIER', '공급사', [('SUP_ID','공급사ID','Integer',True),('SUP_NAME','공급사명','String',False),('CONTACT','연락처','String',False)]),
        ('PURCHASE_ORDER', '구매주문', [('PO_ID','구매주문ID','Integer',True),('SUP_ID','공급사ID','fk-SUPPLIER',False),('PO_DT','주문일자','Date',False),('STATUS_CD','상태코드','String',False)]),
        ('PO_ITEM', '구매주문항목', [('POI_ID','구매주문항목ID','Integer',True),('PO_ID','구매주문ID','fk-PURCHASE_ORDER',False),('PROD_ID','상품ID','fk-PRODUCT',False),('QTY','수량','Integer',False),('UNIT_COST','단가','Decimal',False)]),
        ('GOODS_RECEIPT', '입고', [('GR_ID','입고ID','Integer',True),('PO_ID','구매주문ID','fk-PURCHASE_ORDER',False),('RECV_DT','입고일자','Date',False)]),
        ('STOCK_TAKE', '재고실사', [('ST_ID','실사ID','Integer',True),('WH_ID','창고ID','fk-WAREHOUSE',False),('TAKE_DT','실사일자','Date',False)]),
        ('PRODUCT_BARCODE', '상품바코드', [('BC_ID','바코드ID','Integer',True),('PROD_ID','상품ID','fk-PRODUCT',False),('BARCODE','바코드','String',False)]),
        ('LOT', '로트', [('LOT_ID','로트ID','Integer',True),('PROD_ID','상품ID','fk-PRODUCT',False),('LOT_NO','로트번호','String',False),('EXPIRE_DT','만료일자','Date',False)]),
        ('BIN_LOCATION', '진열위치', [('BIN_ID','위치ID','Integer',True),('WH_ID','창고ID','fk-WAREHOUSE',False),('BIN_CODE','위치코드','String',False)]),
        ('TRANSFER', '이전', [('TR_ID','이전ID','Integer',True),('FROM_WH','출발창고ID','fk-WAREHOUSE',False),('TO_WH','도착창고ID','fk-WAREHOUSE',False),('TRANSFER_DT','이전일자','Date',False)]),
        ('UOM', '단위', [('UOM_CD','단위코드','String',True),('UOM_NAME','단위명','String',False)]),
    ],
    'FINANCE': [
        ('ACCOUNT', '계정', [('ACC_ID','계정ID','Integer',True),('ACC_CD','계정코드','String',False),('ACC_NAME','계정명','String',False),('ACC_TYPE','계정유형','String',False)]),
        ('JOURNAL', '분개', [('JE_ID','분개ID','Integer',True),('JE_DT','분개일자','Date',False),('REF_NO','참조번호','String',False),('DESCRIPTION','설명','String',False)]),
        ('JOURNAL_LINE', '분개라인', [('JEL_ID','분개라인ID','Integer',True),('JE_ID','분개ID','fk-JOURNAL',False),('ACC_ID','계정ID','fk-ACCOUNT',False),('DEBIT_AMT','차변금액','Decimal',False),('CREDIT_AMT','대변금액','Decimal',False)]),
        ('INVOICE', '청구서', [('INV_ID','청구서ID','Integer',True),('INV_NO','청구서번호','String',False),('CUSTOMER_REF','고객참조','String',False),('INV_DT','청구일자','Date',False),('AMT','금액','Decimal',False)]),
        ('PAYMENT', '결제', [('PAY_ID','결제ID','Integer',True),('INV_ID','청구서ID','fk-INVOICE',False),('PAY_DT','결제일자','Date',False),('AMT','금액','Decimal',False)]),
        ('BUDGET', '예산', [('BUD_ID','예산ID','Integer',True),('FISCAL_YR','회계연도','Integer',False),('DEPT_REF','부서참조','String',False),('PLANNED_AMT','계획금액','Decimal',False)]),
        ('TAX_RATE', '세율', [('TAX_CD','세금코드','String',True),('TAX_NAME','세금명','String',False),('RATE','세율','Decimal',False)]),
        ('CURRENCY', '통화', [('CCY_CD','통화코드','String',True),('CCY_NAME','통화명','String',False),('SYMBOL','기호','String',False)]),
        ('FX_RATE', '환율', [('FX_ID','환율ID','Integer',True),('CCY_CD','통화코드','fk-CURRENCY',False),('RATE_DT','적용일자','Date',False),('RATE','환율','Decimal',False)]),
        ('FISCAL_PERIOD', '회계기간', [('FP_ID','회계기간ID','Integer',True),('YR','연도','Integer',False),('PERIOD_NO','기간번호','Integer',False),('STATUS_CD','상태코드','String',False)]),
    ],
}

total = sum(len(t) for t in schemas.values())
print(f'총 {total}개 테이블, {len(schemas)}개 스키마')

# tableId map
table_id = {}
for schema in schemas:
    for tbl_nm, _, _ in schemas[schema]:
        table_id[tbl_nm] = f'cls-{tbl_nm.lower()}'

lines = []
lines.append('<?xml version="1.0" encoding="UTF-8"?>')
lines.append('<xmi:XMI xmi:version="2.1"')
lines.append('         xmlns:uml="http://schema.omg.org/spec/UML/2.1"')
lines.append('         xmlns:xmi="http://schema.omg.org/spec/XMI/2.1">')
lines.append('  <xmi:Documentation exporter="DataQ test sample" exporterVersion="2.0"/>')
lines.append('  <uml:Model xmi:id="model-001" name="DataQ_Test_Model_55Tables">')

pkg_idx = 0
for schema, tables in schemas.items():
    pkg_idx += 1
    lines.append(f'    <packagedElement xmi:type="uml:Package" xmi:id="pkg-{pkg_idx:03d}" name="{schema}">')
    for tbl_nm, tbl_kr, cols in tables:
        cls_id = table_id[tbl_nm]
        lines.append(f'      <packagedElement xmi:type="uml:Class" xmi:id="{cls_id}" name="{tbl_nm}">')
        # 한글명 alias as ownedComment
        lines.append(f'        <ownedComment xmi:type="uml:Comment" xmi:id="{cls_id}-cmt" body="{tbl_kr}"/>')
        for col_nm, col_kr, col_type, is_pk in cols:
            attr_id = f'attr-{cls_id}-{col_nm.lower()}'
            isid_attr = ' isID="true"' if is_pk else ''
            if col_type.startswith('fk-'):
                fk_target = col_type[3:]
                target_id = table_id.get(fk_target, '')
                if target_id:
                    lines.append(f'        <ownedAttribute xmi:type="uml:Property" xmi:id="{attr_id}" name="{col_nm}" type="{target_id}"{isid_attr}>')
                    lines.append(f'          <ownedComment xmi:type="uml:Comment" xmi:id="{attr_id}-cmt" body="{col_kr}"/>')
                    lines.append('          <lowerValue xmi:type="uml:LiteralInteger" value="0"/>')
                    lines.append('        </ownedAttribute>')
                else:
                    lines.append(f'        <ownedAttribute xmi:type="uml:Property" xmi:id="{attr_id}" name="{col_nm}"{isid_attr}>')
                    lines.append(f'          <ownedComment xmi:type="uml:Comment" xmi:id="{attr_id}-cmt" body="{col_kr}"/>')
                    lines.append('          <type xmi:type="uml:PrimitiveType" href="pathmap://UML_LIBRARIES/UMLPrimitiveTypes.library.uml#Integer"/>')
                    lines.append('          <lowerValue xmi:type="uml:LiteralInteger" value="0"/>')
                    lines.append('        </ownedAttribute>')
            else:
                lower = '1' if is_pk else '0'
                lines.append(f'        <ownedAttribute xmi:type="uml:Property" xmi:id="{attr_id}" name="{col_nm}"{isid_attr}>')
                lines.append(f'          <ownedComment xmi:type="uml:Comment" xmi:id="{attr_id}-cmt" body="{col_kr}"/>')
                lines.append(f'          <type xmi:type="uml:PrimitiveType" href="pathmap://UML_LIBRARIES/UMLPrimitiveTypes.library.uml#{col_type}"/>')
                lines.append(f'          <lowerValue xmi:type="uml:LiteralInteger" value="{lower}"/>')
                if is_pk:
                    lines.append('          <upperValue xmi:type="uml:LiteralUnlimitedNatural" value="1"/>')
                lines.append('        </ownedAttribute>')
        lines.append('      </packagedElement>')
    lines.append('    </packagedElement>')

lines.append('  </uml:Model>')
lines.append('</xmi:XMI>')

out = os.path.join(os.path.dirname(__file__), 'sample_xmi_2.1_large.xmi')
with open(out, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))
print(f'생성 완료: {out} ({len(lines)} 라인)')
