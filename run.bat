@echo off
if not exist out mkdir out
javac -encoding UTF-8 -d out src\risk_assessor\*.java || exit /b 1
java -Dfile.encoding=UTF-8 -cp out risk_assessor.Main %*
