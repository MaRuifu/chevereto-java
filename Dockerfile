# syntax=docker/dockerfile:1

# ---------------- 构建阶段 ----------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom.xml：依赖单独一层缓存，改代码不会重复下载依赖
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp package -DskipTests

# ---------------- 运行阶段 ----------------
FROM eclipse-temurin:17-jre-jammy

LABEL org.opencontainers.image.title="chevereto-java" \
      org.opencontainers.image.description="Chevereto V4 image hosting, rewritten in Java (Spring Boot)" \
      org.opencontainers.image.licenses="AGPL-3.0"

# fontconfig + 字体：水印兜底图是用 AWT 现画文字，没有字体就画不出东西
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core tzdata curl \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true -Dfile.encoding=UTF-8"

WORKDIR /app
COPY --from=build /build/target/chevereto-java.jar app.jar

# 数据目录：instance.properties（数据库选择）、内置 H2 库、上传的图片都在这里
RUN groupadd -g 10001 chevereto \
    && useradd -r -u 10001 -g chevereto -d /app -s /usr/sbin/nologin chevereto \
    && mkdir -p /app/data \
    && chown -R chevereto:chevereto /app
USER 10001

VOLUME ["/app/data"]
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS -o /dev/null http://127.0.0.1:8080/ || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--server.port=8080"]
