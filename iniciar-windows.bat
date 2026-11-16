@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ===========================================
echo    HydroGuard Citizen - PostgreSQL
echo ===========================================
where java >nul 2>&1
if errorlevel 1 (
  echo ERRO: Instale Java JDK 17 ou superior.
  pause
  exit /b 1
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo ERRO: Instale Maven 3.9+ ou use docker compose up --build.
  pause
  exit /b 1
)
if not defined DB_PASSWORD (
  echo ERRO: Configure DB_PASSWORD no ambiente antes de executar.
  echo Configure tambem JDBC_URL e DB_USERNAME quando necessario.
  echo Consulte README.md para usar PostgreSQL local ou Docker.
  pause
  exit /b 1
)
echo Abra http://localhost:8080 depois da inicializacao.
echo Login administrativo exige ADMIN_USERNAME e ADMIN_PASSWORD_HASH.
mvn spring-boot:run
pause
