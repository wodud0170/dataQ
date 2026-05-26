# -*- coding: utf-8 -*-
# CAMS 단어 등록안 생성 — Phase 0 후보 502종 큐레이션
import csv

# A: 코멘트 정정 — 비표준 변형어를 행안부 표준어로 치환 (tb_word 미변경, 코멘트 자체를 수정)
#    이음동의어는 사용하지 않고 전부 행안부 표준어(word_nm)로 변경.
ABSORB = {
    # CAMS 고유 변형어
    '붙임': ('첨부', 'ATCH'), '날짜': ('일자', 'YMD'), '에러': ('오류', 'ERR'),
    '유저': ('사용자', 'USER'), '회차': ('차수', 'CYCL'), '사이즈': ('크기', 'SZ'),
    '메세지': ('메시지', 'MSG'), '오픈': ('공개', 'RLS'), '총계': ('합계', 'SUM'),
    '타입': ('유형', 'TYPE'), '미디어': ('매체', 'MEDIA'), '시퀀스': ('순서', 'SEQ'),
    # 행안부 이음동의어 → 표준어로 변경 (CAMS 코멘트에서 실사용된 것)
    'ID': ('아이디', 'ID'), '년도': ('연도', 'YR'), '전': ('이전', 'BFR'),
    '순번': ('일련번호', 'SN'), '세부': ('상세', 'DTL'), '스케줄': ('일정', 'SCHDL'),
    '이름': ('명', 'NM'), '후': ('이후', 'AFTR'), '페이지': ('쪽', 'PAGE'),
    '인수인': ('인수자', 'ACPTR'), '책': ('도서', 'BOOK'), '관련': ('관계', 'REL'),
    '데이터': ('자료', 'DATA'), '공문서': ('공문', 'OFDOC'), '년월': ('연월', 'YM'),
    '명칭': ('명', 'NM'), '전자우편': ('이메일', 'EML'), '홈페이지': ('누리집', 'HMPG'),
    '신청인': ('신청자', 'APLCNT'), '무게': ('중량', 'WGHT'), '지름': ('직경', 'DMTR'),
    '원저자': ('원작자', 'OATHR'), '업데이트': ('갱신', 'UPDT'), '연번': ('일련번호', 'SN'),
    '율': ('비율', 'RT'), '연관': ('관계', 'REL'), '카테고리': ('범주', 'CTGRY'),
    '보수': ('급여', 'SLRY'), '패스워드': ('비밀번호', 'PSWD'), '의뢰자': ('의뢰인', 'RQSR'),
    '송신': ('전송', 'TRSM'), '구입': ('구매', 'PRCHS'), '나이': ('연령', 'AGE'),
    '명수': ('인원수', 'NOPE'), '공백': ('널', 'NUL'), '지체': ('지연', 'DLY'),
    '파라미터': ('변수', 'VRBL'), '암호': ('비밀번호', 'PSWD'),
    # 기관표준 신규(B) 내 이형 통합
    '스캔': ('스캐닝', 'SCAN'), '페이퍼': ('종이', 'PAPER'),
    # 기존 행안부 표준단어로 통합 (약어 충돌 → 표준어 채택)
    '박물': ('박물관', 'MSM'), '태스크': ('업무', 'TASK'), '패스': ('경로', 'PATH'),
    '직권': ('권한', 'AUTHRT'), '도구': ('공구', 'TOOL'), '샘플': ('표본', 'SAMPLE'),
    '휘발성유기화합물': ('VOC', 'VOC'),
}

# B: 기관표준 신규 등록 (한글 -> (영문약어, 비고))  COMM_STND_YN='N'
NEW = {
    '서고': ('STACK', '문서 보존 서고'),
    '기록물건': ('RCDSI', '기록물철 안의 상세 건 — DSID 핵심키 [검토완료]'),
    '유지': ('MNT', '보존환경 유지 50건 / 유지일 2건 [검토완료]'),
    '주민': ('RESID', '주민(등록)'),
    '시청각': ('AUDVS', '시청각 기록물 (오디오·비디오·필름·사진) [검토완료]'),
    '상자': ('BOX', ''),
    '영구': ('PERM', '보존기간 영구'),
    '아카이브': ('ARCV', ''),
    '필름': ('FILM', ''),
    '스캐닝': ('SCAN', ''),
    '준영구': ('SMPERM', '보존기간 준영구 [검토완료]'),
    '총독부': ('GOVGEN', '조선총독부 (총독문서 7건은 별도 코멘트정정 필요) [검토완료]'),
    '미상': ('UNKN', ''),
    '공기질': ('AIRQ', '서고 환경'),
    '소장처': ('HOLDR', '소장처/소장기관'),
    '원어': ('ORGWD', '원어'),
    '구술': ('ORAL', '구술 기록'),
    '대통령': ('PRSDT', ''),
    '비밀': ('SECR', '비밀(보안등급)'),
    '납본': ('LGDPST', '납본'),
    '초안': ('DRAFT', ''),
    '띄어쓰기': ('SPACNG', ''),
    '복제': ('DUPL', ''),
    '클래스': ('CLASS', 'OOP 클래스'),
    '계열': ('SERIES', ''),
    '시소러스': ('THSRS', '시소러스'),
    '종이': ('PAPER', ''),
    '해쉬': ('HASH', '해쉬=해시'),
    '서열': ('RANK', ''),
    '소장': ('HOLD', '소장(보유)'),
    '감염': ('INFCT', '바이러스 감염'),
    '정정': ('CORRCT', ''),
    '디지털': ('DGTL', ''),
    '지도': ('MAP', ''),
    '데몬': ('DAEMON', ''),
    '육안': ('NKDEYE', '육안 검사 — 확인필요'),
    '번지': ('BUNJI', '주소 번지'),
    '콜렉션': ('CLLCTN', '콜렉션=컬렉션'),
    '메타': ('META', ''),
    '볼륨': ('VOL', ''),
    '백신': ('VACCN', ''),
    '체크': ('CHK', ''),
    '시대': ('ERA', ''),
    '봉투': ('ENVLP', ''),
    '자기': ('MAGNET', '자기(테이프) — 확인필요'),
    '연동': ('INTRLK', ''),
    '비교': ('CMPR', ''),
    '컨텐츠': ('CONTNT', '컨텐츠=콘텐츠'),
    '커맨드': ('CMMND', ''),
    '질의어': ('QRYWD', ''),
    '현존': ('EXIST', ''),
    '소장인': ('HOLDER', '소장인'),
    '통권': ('TOTVOL', '통권(누적 권호)'),
    '카운트': ('COUNT', ''),
    '이유': ('REASON', ''),
    '협정': ('AGRMT', ''),
    '역사': ('HIST', ''),
    '유출': ('LEAK', ''),
    '편철': ('FILING', '편철'),
    '디지털화': ('DGTLZ', ''),
    '광디스크': ('OPTDSK', ''),
    '헤더': ('HEADER', ''),
    '그림': ('PICTR', ''),
    '서브': ('SUB', ''),
    '서면': ('WRITTN', ''),
    '녹음': ('RECORD', ''),
    '질의': ('QUERY', ''),
    '소화': ('FIRESP', '소화(소방) — 확인필요'),
    '교류': ('EXCHNG', ''),
    '예고': ('NOTICE', ''),
    '판단': ('JUDGE', ''),
    '플래그': ('FLAG', ''),
    '경광등': ('WRNLMP', ''),
    '판본': ('EDITN', ''),
    '대외비': ('CONFID', '대외비(보안)'),
    '컴포넌트': ('COMPNT', ''),
    '이중': ('DOUBLE', ''),
    '준칙': ('GUIDE', '준칙'),
    '세항': ('SUBITM', ''),
    '추적': ('TRACE', ''),
    '오디오': ('AUDIO', ''),
    '비디오': ('VIDEO', ''),
    '주변': ('AROUND', ''),
    '상정': ('SUBMIT', '상정 — 확인필요'),
    '사료': ('HISTMT', '사료(史料)'),
    '무단': ('UNAUTH', ''),
    '증정인': ('DONOR', '증정인'),
    '액션': ('ACTION', ''),
    '포탈': ('PORTAL', '포탈=포털'),
    '마스터': ('MASTER', ''),
    '코너': ('CORNER', ''),
    '메서드': ('METHOD', ''),
    '롤필름': ('ROLFLM', ''),
    '파싱': ('PARSNG', ''),
    '현상기': ('DEVELP', '필름 현상기'),
    '경향': ('TENDNC', ''),
    '타당성': ('VALDTY', ''),
    '앨범': ('ALBUM', ''),
    '색채': ('COLOR', ''),
    '재생': ('PLAY', ''),
    '잉크': ('INK', ''),
    '두께': ('THCKNS', ''),
    '신조어': ('NEWWRD', ''),
    '복제본': ('DUPLCT', ''),
    '네트워크': ('NETWRK', ''),
    '드라이브': ('DRIVE', ''),
    '클립': ('CLIP', ''),
    '워커': ('WORKER', ''),
    '라이센스': ('LICENS', '라이센스=라이선스'),
    '시청': ('WATCH', '시청(視聽)'),
    '인화': ('PHTPRT', '사진 인화'),
    '마이그레이션': ('MGRTN', '마이그레션(오타)도 동의어로 흡수'),
    '진본': ('AUTHEN', '진본(眞本)'),
    '시점': ('TMPNT', ''),
    '타겟': ('TARGET', ''),
    '음화': ('NEGTV', '음화(陰畫)'),
    '양화': ('POSTV', '양화(陽畫)'),
    '편집자': ('EDITOR', ''),
    '감독': ('DRCTR', ''),
    '감독관': ('SUPVSR', ''),
    '워터마크': ('WTRMRK', ''),
    '되감기': ('REWIND', ''),
    '세척': ('CLEAN', ''),
    '레코딩': ('RECDNG', ''),
    '음향': ('SOUND', ''),
    '용기': ('CONTNR', '용기(容器)'),
    '변색': ('DISCLR', ''),
    '포지션': ('POSITN', ''),
    '작사자': ('LYRICST', ''),
    '작곡자': ('COMPSR', ''),
    '기구': ('MCHNRY', '기구(器具) — 확인필요'),
    '샘플링': ('SAMPLNG', ''),
    '실물': ('REALOBJ', ''),
    '획득': ('ACQUIRE', ''),
    '손망실': ('LSDMG', '손망실(損亡失)'),
    '오브젝트': ('OBJECT', ''),
    '어셋': ('ASSET', '어셋=에셋'),
    '시트': ('SHEET', ''),
    '합사': ('ENSHRN', '야스쿠니 합사 — 총독부 기록'),
    '훈장류': ('MEDAL', ''),
    '디지털사인': ('DGTLSGN', ''),
    '타임스탬프': ('TMSTMP', ''),
    '광': ('LIGHT', '광(光) — 확인필요'),
    '질소': ('NITRGN', '서고 환경 측정'),
    '포름알데히드': ('FORMALD', '서고 환경 측정'),
    '주간지': ('WKMGZ', '보도매체 주/월간지 [검토완료]'),
    '월간지': ('MTMGZ', '보도매체 주/월간지 [검토완료]'),
    '커트': ('CUT', ''),
    '회로': ('CIRC', '폐쇄회로감시장치 2건 [검토완료]'),
}

# B 단어영문명 (full English name) — 한글 -> 영문명
ENG = {
    '서고': 'Stack', '기록물건': 'Records Item',
    '유지': 'Maintenance', '주민': 'Resident', '시청각': 'Audio Visual',
    '상자': 'Box', '영구': 'Permanent', '필름': 'Film',
    '스캐닝': 'Scanning', '준영구': 'Semi-permanent', '총독부': 'Government-General',
    '미상': 'Unknown', '공기질': 'Air Quality', '소장처': 'Holding Location',
    '원어': 'Original Word', '구술': 'Oral', '대통령': 'President', '비밀': 'Secret',
    '납본': 'Legal Deposit', '초안': 'Draft', '띄어쓰기': 'Spacing', '복제': 'Duplication',
    '클래스': 'Class', '계열': 'Series', '시소러스': 'Thesaurus', '종이': 'Paper',
    '해쉬': 'Hash', '서열': 'Rank', '소장': 'Holding', '감염': 'Infection',
    '정정': 'Correction', '디지털': 'Digital', '지도': 'Map', '데몬': 'Daemon',
    '육안': 'Naked Eye', '번지': 'Lot Number', '콜렉션': 'Collection', '메타': 'Meta',
    '볼륨': 'Volume', '백신': 'Vaccine', '체크': 'Check', '시대': 'Era',
    '봉투': 'Envelope', '자기': 'Magnetic', '연동': 'Interlock', '비교': 'Comparison',
    '컨텐츠': 'Content', '커맨드': 'Command', '질의어': 'Query Word', '현존': 'Existence',
    '소장인': 'Holder', '통권': 'Total Volume', '카운트': 'Count', '이유': 'Reason',
    '협정': 'Agreement', '역사': 'History', '유출': 'Leak', '편철': 'Filing',
    '디지털화': 'Digitalization', '광디스크': 'Optical Disk', '헤더': 'Header',
    '그림': 'Picture', '서브': 'Sub', '서면': 'Written', '녹음': 'Recording',
    '질의': 'Query', '소화': 'Fire Suppression', '교류': 'Exchange', '예고': 'Notice',
    '아카이브': 'Archive', '판단': 'Judgement', '플래그': 'Flag', '경광등': 'Warning Lamp',
    '판본': 'Edition', '대외비': 'Confidential', '컴포넌트': 'Component', '이중': 'Double',
    '준칙': 'Guideline', '세항': 'Sub Item', '추적': 'Trace', '오디오': 'Audio',
    '비디오': 'Video', '주변': 'Surroundings', '상정': 'Submission', '사료': 'Historical Material',
    '무단': 'Unauthorized', '증정인': 'Donor', '액션': 'Action', '포탈': 'Portal',
    '마스터': 'Master', '코너': 'Corner', '메서드': 'Method', '롤필름': 'Roll Film',
    '파싱': 'Parsing', '현상기': 'Developer', '경향': 'Tendency', '타당성': 'Validity',
    '앨범': 'Album', '색채': 'Color', '재생': 'Play', '잉크': 'Ink', '두께': 'Thickness',
    '신조어': 'New Word', '복제본': 'Duplicate', '네트워크': 'Network', '드라이브': 'Drive',
    '클립': 'Clip', '워커': 'Worker', '라이센스': 'License', '시청': 'Watching',
    '인화': 'Photo Print', '훈장류': 'Medal', '질소': 'Nitrogen', '포름알데히드': 'Formaldehyde',
    '마이그레이션': 'Migration', '진본': 'Authentic', '시점': 'Time Point', '타겟': 'Target',
    '음화': 'Negative', '양화': 'Positive', '편집자': 'Editor', '감독': 'Director',
    '감독관': 'Supervisor', '워터마크': 'Watermark', '되감기': 'Rewind', '세척': 'Cleaning',
    '레코딩': 'Recording', '음향': 'Sound', '용기': 'Container', '변색': 'Discoloration',
    '포지션': 'Position', '작사자': 'Lyricist', '작곡자': 'Composer', '기구': 'Machinery',
    '샘플링': 'Sampling', '실물': 'Real Object', '획득': 'Acquisition', '손망실': 'Loss and Damage',
    '오브젝트': 'Object', '어셋': 'Asset', '시트': 'Sheet', '합사': 'Enshrinement',
    '디지털사인': 'Digital Sign', '타임스탬프': 'Timestamp',
    '주간지': 'Weekly Magazine', '월간지': 'Monthly Magazine',
    '커트': 'Cut', '광': 'Light', '회로': 'Circuit',
}

# C: 제외 — 분해잔여/코드값/숫자/고유제품명 (등록 안 함)
EXCLUDE_NOTE = '분해잔여·코드값·고유명사 — 등록 제외(사전 보강 후 재분해 시 소멸)'

# 출현수 로드 (B 신규는 후보 TSV, A 행안부변형어는 분해 토큰 집계)
cnt = {
    'ID': 1898, '년도': 261, '전': 136, '순번': 125, '세부': 109, '스케줄': 72,
    '이름': 68, '후': 54, '페이지': 46, '인수인': 42, '책': 34, '관련': 31,
    '데이터': 28, '공문서': 25, '년월': 15, '명칭': 10, '전자우편': 9, '홈페이지': 9,
    '신청인': 9, '무게': 6, '지름': 5, '원저자': 5, '업데이트': 5, '연번': 4,
    '율': 4, '연관': 3, '카테고리': 3, '보수': 3, '패스워드': 3, '의뢰자': 3,
    '송신': 2, '구입': 2, '나이': 1, '명수': 1, '공백': 1, '지체': 1, '파라미터': 1,
}
with open('단어후보_전체.tsv', encoding='utf-8') as f:
    for row in csv.reader(f, delimiter='\t'):
        if len(row) == 3 and row[0] == '한글2자+':
            cnt[row[1]] = int(row[2])

# 단어_등록안.tsv — TB_WORD 에 실제 등록할 기관표준 신규 단어 (B 만)
with open('단어_등록안.tsv', 'w', encoding='utf-8', newline='') as f:
    w = csv.writer(f, delimiter='\t')
    w.writerow(['한글', '출현수', '영문약어', '영문명', '도메인분류', '비고'])
    for k, (ab, note) in sorted(NEW.items(), key=lambda x: -cnt.get(x[0], 0)):
        w.writerow([k, cnt.get(k, ''), ab, ENG.get(k, ''), '', note])
    miss = [k for k in NEW if k not in ENG]
    if miss:
        print('  [경고] 영문명 누락:', miss)

# 코멘트정정_규칙.tsv — 코멘트의 변형어를 행안부 표준어로 치환 (등록 아님, tb_word 미변경)
with open('코멘트정정_규칙.tsv', 'w', encoding='utf-8', newline='') as f:
    w = csv.writer(f, delimiter='\t')
    w.writerow(['변형어', '출현수', '표준어(행안부)', '표준약어', '비고'])
    for k, (tgt, ab) in sorted(ABSORB.items(), key=lambda x: -cnt.get(x[0], 0)):
        w.writerow([k, cnt.get(k, ''), tgt, ab, '코멘트 %s→%s 치환' % (k, tgt)])

a, b = len(ABSORB), len(NEW)
print('단어_등록안.tsv     — B 기관표준 신규 등록 %d종' % b)
print('코멘트정정_규칙.tsv — A 변형어→표준어 치환 %d종 (등록 아님)' % a)
chk = sum(1 for v in NEW.values() if '확인필요' in v[1])
print('  B 중 확인필요 표시: %d종' % chk)
print('  후보 502종 중 미채택(C 제외): %d종' % (502 - a - b))
