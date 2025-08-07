# Chronicle Queue 설정 가이드

## ⚠️ 중요: JVM 인수 필수

Chronicle Queue를 Java 11+ 환경에서 사용하려면 **반드시** 다음 JVM 인수가 필요합니다.
이 인수들 없이 실행하면 `java.lang.IllegalAccessException` 오류가 발생합니다.

## 필요한 JVM 인수

Chronicle Queue를 Java 11+ 환경에서 사용하려면 다음 JVM 인수가 필요합니다:

```bash
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.nio=ALL-UNNAMED  
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
--add-opens java.base/sun.nio.fs=ALL-UNNAMED
--add-opens java.base/java.lang.invoke=ALL-UNNAMED
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED
--add-exports java.base/sun.nio.ch=ALL-UNNAMED
--add-exports jdk.unsupported/sun.misc=ALL-UNNAMED
--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
```

## 애플리케이션 실행 방법

### 1. Gradle을 사용한 실행
```bash
./gradlew bootRun
```
Gradle 설정에 이미 JVM 인수가 포함되어 있습니다.

### 2. 직접 실행 스크립트
```bash
./run.sh
```
제공된 스크립트를 사용하면 필요한 JVM 인수가 자동으로 설정됩니다.

### 3. IDE에서 실행
IDE에서 실행할 때는 Run Configuration의 VM Options에 위의 JVM 인수를 추가하세요.

**IntelliJ IDEA:**
1. Run → Edit Configurations...
2. VM options 필드에 JVM 인수 추가
3. Use classpath of module 선택

**Eclipse:**
1. Run → Run Configurations...
2. Arguments 탭에서 VM arguments에 JVM 인수 추가

**VSCode:**
launch.json의 vmArgs에 추가:
```json
{
    "vmArgs": "--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED ..."
}
```

## 설정 확인

애플리케이션이 성공적으로 시작되면 다음과 같은 로그를 볼 수 있습니다:

```
Chronicle OrderQueue를 생성합니다. 경로: ./data/queues/orders
ChronicleOrderQueue 초기화 완료 - 기존 메시지 수: 0
Chronicle TradeResultQueue를 생성합니다. 경로: ./data/queues/trades  
ChronicleTradeResultQueue 초기화 완료 - 기존 메시지 수: 0
매칭 엔진 초기화 완료: 5 개 심볼
```

## 디렉토리 구조

Chronicle Queue 파일은 다음 위치에 저장됩니다:
```
./data/queues/orders/    # 주문 큐 데이터
./data/queues/trades/    # 거래 결과 큐 데이터
```

이 디렉토리들은 애플리케이션이 시작될 때 자동으로 생성됩니다.