# ============================================================
# 데이터 품질 진단 테스트 — 4개 SQL 일괄 적용
# ============================================================
# 사전 조건:
#   - dataq-db (PostgreSQL 13) 컨테이너 기동 중
#   - q-center / q-executor 가 본 DB 를 사용 중
#
# 실행:
#   PowerShell> .\apply_all.ps1
#   bash> ./apply_all.sh   (별도 작성 필요 — 본 PS1 만 1차 제공)
# ============================================================

$ErrorActionPreference = "Stop"
$container = "dataq-db"
$dbUser    = "admin"
$dbName    = "postgres"
$here      = Split-Path -Parent $MyInvocation.MyCommand.Definition

$files = @(
    "01_qual_test_ddl.sql",
    "02_qual_test_data.sql",
    "03_qual_test_metadata.sql",
    "04_qual_test_rules.sql"
)

# 컨테이너 가동 확인
$running = docker ps --filter "name=$container" --format "{{.Names}}" 2>$null
if ($running -ne $container) {
    Write-Host "[ERROR] $container 컨테이너가 실행되지 않았습니다." -ForegroundColor Red
    Write-Host "        docker compose up -d (또는 docker start $container) 후 다시 시도." -ForegroundColor Red
    exit 1
}

foreach ($f in $files) {
    $path = Join-Path $here $f
    if (-not (Test-Path $path)) {
        Write-Host "[ERROR] 파일 없음: $path" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
    Write-Host "=============================================" -ForegroundColor Cyan
    Write-Host " 적용: $f" -ForegroundColor Cyan
    Write-Host "=============================================" -ForegroundColor Cyan

    docker cp $path "${container}:/tmp/$f" | Out-Null
    docker exec -e PGOPTIONS="-c client_min_messages=warning" $container `
        psql -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -f "/tmp/$f"

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] $f 적용 실패. 중단." -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "=============================================" -ForegroundColor Green
Write-Host " ALL DONE — 다음 단계:" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
Write-Host "  1) dataQ UI 로그인 → '데이터 품질 진단' 메뉴 → 업무 규칙 관리 → 모델 'TEST_QUAL_MODEL' 선택"
Write-Host "  2) 16개 룰이 보이는지 확인"
Write-Host "  3) [진단 실행] 클릭 → 결과 화면에서 위반 카운트 확인"
Write-Host "     예상 위반은 README.md 의 표 참고"
Write-Host ""
Write-Host "  (정리할 때) docker cp 99_qual_test_cleanup.sql ${container}:/tmp/ && docker exec $container psql -U $dbUser -d $dbName -f /tmp/99_qual_test_cleanup.sql"
