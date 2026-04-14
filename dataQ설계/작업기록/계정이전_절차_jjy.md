# 새 계정 `jjy`로 개발환경 이전 절차

`장재영` 계정(한글 프로필) → `jjy` 계정(ASCII 프로필) 이전 체크리스트.
목적: VSCode Java LSP가 한글 경로 probe 문제 없이 정상 기동되는 개발환경 구축.

## 0. 이전 전 확인 (현재 계정에서)

- [ ] 현재 작업 커밋·푸시 완료 확인: `git status` 깨끗, `git log origin/main..HEAD` 비어있음
- [ ] Docker Desktop 실행 중인 컨테이너 기록해 둘 것
  - `docker ps -a` 결과 메모 (이름, 이미지, 포트)
  - dataq-db 컨테이너는 WSL2 백엔드 내부에 있어 계정 바꿔도 유지됨
- [ ] VSCode 확장 목록 내보내기 (선택)
  - `code --list-extensions > %USERPROFILE%\Desktop\vscode-extensions.txt`

## 1. `jjy` 계정으로 로그인

- 시작 → 사용자 전환 → `jjy` (비밀번호 `dev123`)
- 첫 로그인 시 Windows 프로필 초기화 (몇 분 소요)
- 프로필 경로 확인: `C:\Users\jjy` (ASCII, 한글 없음)

## 2. 기본 시스템 확인

새 계정에서 cmd 열고:

```cmd
echo %USERPROFILE%
java -version
mvn -version
git --version
docker --version
```

- **JDK, Maven, Git, Docker**: 시스템 전역 설치라 새 계정에서도 그대로 사용 가능
- **PATH**: 시스템 변수는 공유됨. 사용자 변수(`%USERPROFILE%\...`)에 박혀있던 건 없는지 확인
- **JAVA_HOME**: 시스템 변수로 설정되어 있는지 확인 (`C:\Program Files\Java\jdk1.8.0_202`)

## 3. 프로젝트 파일 가져오기

### 방법 A. git clone (권장)

```cmd
cd C:\Users\jjy\Desktop
git clone https://github.com/wodud0170/dataQ.git
```

- 장점: 깨끗한 상태, 한글 경로 완전 제거
- GitHub 자격 증명: 첫 push/clone 시 Git Credential Manager가 브라우저 로그인 요구 → 이후 `jjy` 계정 자격 증명 관리자에 저장

### 방법 B. 기존 폴더 복사

```cmd
xcopy /E /I /H "C:\Users\장재영\Desktop\dataQ" "C:\Users\jjy\Desktop\dataQ"
```

- 주의: `.git` 폴더도 복사됨. 미푸시 브랜치/stash/untracked가 그대로 넘어옴
- `dataq-db\dataq-db.tar` 같은 대용량 파일도 같이 복사됨 — 불필요하면 제외

## 4. Docker Desktop 설정

1. Docker Desktop 실행 (시작 메뉴)
2. 첫 실행 시 WSL 통합 체크 재확인
   - Settings → Resources → WSL Integration → 필요한 배포판 활성화
3. 기존 이미지/컨테이너 확인:
   ```cmd
   docker images
   docker ps -a
   ```
   - WSL2 백엔드 데이터는 계정 무관하게 공유되므로 `dataq-db:latest`, 실행 중이던 컨테이너가 보여야 정상
4. dataq-db 컨테이너가 멈춰 있으면 재기동:
   ```cmd
   docker start <container-name>
   ```
   (이름은 0단계에서 메모한 것 사용. 없으면 새로 run)

## 5. VSCode 설치/설정

### 5-1. VSCode 실행
- 시스템 전역 설치라면 시작 메뉴에서 바로 실행 가능
- 사용자 설치(`%LOCALAPPDATA%\Programs\Microsoft VS Code`)였다면 `jjy` 계정에서 재설치 필요

### 5-2. 확장 설치
필수:
- `vscjava.vscode-java-pack` (Extension Pack for Java)
- `vmware.vscode-boot-dev-pack` (Spring Boot Extension Pack)
- `redhat.vscode-xml` (MyBatis XML 편집용)
- `Vue.volar` 또는 `octref.vetur` (Vue 2 → Vetur)
- `Anthropic.claude-code` (Claude Code 확장)

이전 계정 확장 목록 파일(`vscode-extensions.txt`)이 있다면:
```cmd
for /f %i in (C:\Users\jjy\Desktop\vscode-extensions.txt) do code --install-extension %i
```

### 5-3. settings.json
장재영 계정에서 적용했던 Java 경로 설정은 **불필요** (번들 JDK 21로 충분).
필요 시 최소 설정:
```json
{
    "claudeCode.preferredLocation": "panel"
}
```

### 5-4. 프로젝트 열기
```cmd
code C:\Users\jjy\Desktop\dataQ
```
- JDT Language Server가 `C:\Users\jjy\AppData\Roaming\Code\User\workspaceStorage\...` 에 초기화 (ASCII 경로)
- Java 8 probe도 ASCII 경로에서 실행되어 MS949 문제 없음
- Spring Boot Dashboard에 q-center / q-executor apps가 정상적으로 보여야 함

## 6. 빌드·실행 확인

```cmd
cd C:\Users\jjy\Desktop\dataQ\q-common
mvn install -q -DskipTests

cd ..\q-center
mvn package -q -DskipTests

cd ..\q-executor
mvn package -q -DskipTests
```

- 빌드 성공 확인
- q-center 실행 → `http://localhost:28091` 접속
- DB 연결 확인 (25433 포트의 dataq-db 컨테이너)

## 7. 기타 개발 도구 (필요 시)

- **DBeaver / DataGrip**: 재설치 (사용자 데이터가 프로필에 있음)
- **Postman**: 재설치, 기존 컬렉션은 동기화 또는 export/import
- **브라우저 북마크**: Chrome/Edge 동기화 계정 사용 시 자동 복구
- **SSH 키**: `%USERPROFILE%\.ssh\` 복사 필요 시 수동 이동
- **GitHub 자격 증명**: Git Credential Manager가 `jjy` 계정에서 새로 로그인 요구

## 8. 롤백 / 트러블슈팅

### `jjy`에서 VSCode Java LSP가 여전히 실패하면
- workspaceStorage 경로가 정말 ASCII인지 확인: `%APPDATA%\Code\User\workspaceStorage`
- 프로젝트 경로도 ASCII인지 확인 (실수로 `C:\Users\장재영\...` 열면 의미 없음)
- 번들 JDK 21 사용 여부 확인: `redhat.java` 출력 로그에서 `jre\21.0.10-win32-x86_64` 경로 확인

### Docker Desktop이 이미지를 못 찾으면
- WSL 통합 재설정 후 Docker Desktop 재시작
- 최악의 경우: `장재영` 계정으로 로그인해서 `docker save` → `jjy`에서 `docker load`

### 장재영 계정 데이터 접근
- `C:\Users\장재영\...`는 `jjy`가 관리자라면 권한 부여 후 접근 가능
- 중요한 파일(SSH 키, 인증서, 개인 설정 등)은 사전에 공용 경로로 이동해 두면 편함

## 9. 장재영 계정 처리

- **당분간 유지**: 이전 완료 직후 바로 삭제하지 말 것. 빠진 파일·설정 복구용으로 남겨둠
- **충분히 검증된 후** (1~2주): 필요 없으면 설정 → 계정 → 기타 사용자에서 삭제
- 레지스트리 백업 `C:\Users\장재영\Desktop\javasoft-backup.reg`는 JavaSoft 키 복구가 필요 없다면 삭제해도 됨

## 10. 장기 원칙

- 앞으로 Windows 계정은 **항상 ASCII**로 생성
- 프로젝트 루트는 **한글 경로 금지** (`C:\dev\`, `D:\workspace\` 같은 곳에 두는 것 권장)
- 한글 파일명은 허용 — 문제는 Java 프로세스의 classpath / JVM 시작 시점 경로에만 한정됨
