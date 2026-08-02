@echo off
set JAVA_HOME=D:\Android Studio\jbr
set GRADLE_HOME=C:\Users\Alireza\Desktop\gradle-8.14.3
set PATH=%GRADLE_HOME%\bin;%JAVA_HOME%\bin;%PATH%
cd /d "D:\Dev\TAR project\v1.4\android"
"%GRADLE_HOME%\bin\gradle.bat" assembleRelease -Pc2_host=152.67.155.202 -Pc2_port=8230 -Pcrypto_key="v3ilm4sk2024veil" -Penroll_key="changeme"
