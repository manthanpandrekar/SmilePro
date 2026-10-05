FROM node:22 AS fe

WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm install

COPY frontend ./
RUN npm run build


FROM maven:3.9-eclipse-temurin-21 AS be

WORKDIR /app

COPY backend ./backend

COPY --from=fe /app/frontend/dist ./backend/src/main/resources/static

RUN cd backend && mvn -q package -DskipTests


FROM eclipse-temurin:21-jre

COPY --from=be /app/backend/target/smilepro.jar /app.jar

EXPOSE 8080

CMD ["java", "-jar", "/app.jar"]