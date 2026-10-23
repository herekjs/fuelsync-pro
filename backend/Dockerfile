FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# 프론트엔드는 Vercel에서 따로 배포하므로 Node 설치/빌드 단계를 건너뜀
RUN mvn -q -DskipTests -Dskip.installnodenpm -Dskip.npm package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.war app.war
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.war"]
