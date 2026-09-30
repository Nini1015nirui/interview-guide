#!/usr/bin/env bash
# 编译并运行全部实验。参数：项目 resources 目录（默认指向仓库内的 app/src/main/resources）。
set -euo pipefail
cd "$(dirname "$0")"
RESOURCES="${1:-../../app/src/main/resources}"
mvn -q -B compile
CP="$(mvn -q -B dependency:build-classpath -Dmdep.outputFile=/dev/stdout)"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "target/classes:${CP}" lab.AgentLab "${RESOURCES}"
