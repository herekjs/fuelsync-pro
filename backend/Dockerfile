# 빌드 컨텍스트는 저장소 루트 (render.yaml 참고)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml .
COPY backend/src ./src
# 프론트엔드는 Vercel에서 따로 배포하므로 Node 설치/빌드 단계를 건너뜀
RUN mvn -q -DskipTests -Dskip.installnodenpm -Dskip.npm package

FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends mariadb-server \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.war app.war
COPY db/schema.sql schema.sql
COPY db/seed.sql seed.sql
COPY backend/docker-entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
ENV DB_URL="jdbc:mariadb://127.0.0.1:3306/fuelsync?characterEncoding=UTF-8&serverTimezone=Asia/Seoul" \
    DB_USERNAME=fuelsync \
    DB_PASSWORD=fuelsync-local-only
EXPOSE 8080
CMD ["./entrypoint.sh"]
