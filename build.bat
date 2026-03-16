@echo off

echo ==================================
echo Killing server ports
echo ==================================

REM 8080 포트 kill
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080') do (
    echo Killing PID %%a on port 8080
    taskkill /F /PID %%a >nul 2>&1
)

REM 280xx 포트 kill
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :280') do (
    echo Killing PID %%a on port 280xx
    taskkill /F /PID %%a >nul 2>&1
)

echo ==================================
echo Maven Build Start
echo ==================================

mvn clean package -DskipTests -T 1C

if %ERRORLEVEL% neq 0 (
    echo Build failed!
    pause
    exit /b
)

echo ==================================
echo Build Complete - Starting Servers
echo ==================================

set ND_HOME=/c/Users/naraedata/Desktop/dataQ/ndata-quality-master

start "q-center" "C:\Program Files\Git\git-bash.exe" -c "cd /c/Users/naraedata/Desktop/dataQ/ndata-quality-master/q-center/target && ND_HOME=%ND_HOME% java -jar q-center-0.0.1-SNAPSHOT.jar; exec bash"

start "q-executor" "C:\Program Files\Git\git-bash.exe" -c "cd /c/Users/naraedata/Desktop/dataQ/ndata-quality-master/q-executor/target && ND_HOME=%ND_HOME% java -jar q-executor.jar; exec bash"

start "vue-front" "C:\Program Files\Git\git-bash.exe" -c "cd /c/Users/naraedata/Desktop/dataQ/ndata-quality-master/q-center/vue/front && npm run dev; exec bash"

echo ==================================
echo All services started
echo ==================================

pause