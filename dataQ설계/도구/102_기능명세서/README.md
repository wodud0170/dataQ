# 102번 기능명세서 생성 도구

102번 기능명세서(md)를 고친 뒤 부록 A 와 대표 보고용 HTML 을 다시 만들 때 쓴다. Node.js 만 있으면 된다. 저장소의 소스는 읽기만 한다.

| 파일 | 하는 일 |
|---|---|
| `api_list.js` | q-center·q-executor 컨트롤러의 매핑 어노테이션에서 메서드·경로 조합을 뽑아 이 폴더에 `api_list.tsv` 를 만든다 |
| `api_cover.js` | 명세 md 의 기능 표 행에서 경로를 찾아 엔드포인트별 기능ID 를 모은다. 이 폴더에 `api_appendix.md`(부록 A 표)를 만들고, 어느 행에도 없는 경로를 `MISSING` 으로 출력한다. 품질 진단 컨트롤러(`Qual*`)는 뺀다 |
| `build.js` + `template.html` | 명세 md 를 읽어 대표 보고용 HTML(구성도·영역별 규모·메뉴·업무 흐름·구현 범위·기능 목록 검색)을 만든다. 장 요약 문장(`SUMMARY`)과 짧은 이름(`SHORT`)은 `build.js` 안에 있다 |

## 순서

저장소 루트에서:

```bash
node "dataQ설계/도구/102_기능명세서/api_list.js"
node "dataQ설계/도구/102_기능명세서/api_cover.js" "dataQ설계/102_기능명세서_2026-10-12.md"
node "dataQ설계/도구/102_기능명세서/build.js" "dataQ설계/102_기능명세서_2026-10-12.md" "dataQ설계/도구/102_기능명세서/template.html" "dataQ설계/102_기능명세서_2026-10-12.html"
```

1. `api_cover.js` 가 `MISSING` 을 출력하면 그 경로를 다루는 기능 행을 명세에 추가한다.
2. `api_appendix.md` 의 표로 명세 md 의 "부록 A" 표를 바꾼다. 표 위 설명 문장의 개수(경로 수, q-center·q-executor 수)도 맞춘다.
3. `build.js` 가 출력하는 기능 수·화면 수와 `template.html` 의 고정 숫자(API 수, 컨트롤러별 API 수, 구성도의 API 수)가 맞는지 본다.
4. HTML 을 claude.ai 아티팩트로 다시 게시하는 경우 같은 파일 경로로 게시하면 링크가 유지된다.

`api_list.tsv` 와 `api_appendix.md` 는 생성물이라 커밋하지 않아도 된다.
