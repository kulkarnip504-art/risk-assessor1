#!/bin/sh
# Usage: sh run.sh backtest        (prints what the system would have done to last month)
#        sh run.sh serve           (dashboard + WhatsApp simulator at http://localhost:8080)
set -e
mkdir -p out
${JAVAC:-javac} -encoding UTF-8 -d out src/codguard/*.java
java -Dfile.encoding=UTF-8 -cp out codguard.Main "$@"
