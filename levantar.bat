@echo off
REM Levanta los tres servicios de AURA, uno por ventana.
REM Las claves salen de .env (que esta gitignoreado): aca no hay secretos.
setlocal
cd /d "%~dp0"

if not exist ".env" (
  echo.
  echo   FALTA el archivo .env en la raiz de deploy.
  echo   Copia .env.example a .env y completa las claves.
  echo.
  pause
  exit /b 1
)

REM Cada linea del .env pasa a ser variable de entorno de esta consola,
REM y las ventanas que se abren abajo las heredan. eol=# salta comentarios.
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"

if "%DB_PASS%"=="" (
  echo   .env no define DB_PASS.
  pause
  exit /b 1
)
if "%JWT_SECRET%"=="" (
  echo   .env no define JWT_SECRET: el backend no arranca sin eso.
  pause
  exit /b 1
)

echo Levantando IA (8000)...
start "AURA IA :8000" /D "%~dp0aura_chatbot" cmd /k "py -3 -m uvicorn main:app --host 127.0.0.1 --port 8000"

echo Levantando Backend (8080)...
start "AURA Backend :8080" /D "%~dp0backend\project-aura" cmd /k "mvnw.cmd spring-boot:run"

echo Levantando Frontend (3000)...
start "AURA Frontend :3000" /D "%~dp0frontend\aura-frontend" cmd /k "set BROWSER=none&&npm start"

echo.
echo   Listo: se abrieron 3 ventanas.
echo   El backend tarda ~30s en quedar disponible; el frontend ~20s.
echo   Entra a http://localhost:3000 con el usuario %ADMIN_EMAIL%
echo.
echo   Ojo: Ollama (11434) es aparte y tiene que estar corriendo.
echo.
