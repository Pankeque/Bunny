@echo off
setlocal

set APP_HOME=%~dp0

set CLASSPATH=%APP_HOME%gradle/wrapper/gradle-wrapper.jar

set JAVA_HOME=%JAVA_HOME%
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if "%JAVA_HOME%" == "" goto noJavaHome
if exist "%JAVA_EXE%" goto javaFound

:noJavaHome
echo ERROR: JAVA_HOME is not set
exit /b 1

:javaFound
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
