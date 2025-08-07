#!/bin/bash

# Chronicle Queue를 위한 JVM 인수
JVM_ARGS="--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
--add-opens java.base/java.io=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
--add-opens java.base/sun.nio.fs=ALL-UNNAMED \
--add-opens java.base/java.lang.invoke=ALL-UNNAMED \
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
--add-exports java.base/sun.nio.ch=ALL-UNNAMED \
--add-exports jdk.unsupported/sun.misc=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"

echo "Quantum Exchange 서버를 시작합니다..."
echo "JVM 인수: $JVM_ARGS"

# 필요한 디렉토리 생성
mkdir -p ./data/queues/orders
mkdir -p ./data/queues/trades

# 애플리케이션 실행
./gradlew bootRun