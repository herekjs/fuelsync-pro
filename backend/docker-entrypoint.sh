#!/bin/sh
# 데모용: 컨테이너 안에서 MariaDB를 띄우고 db/init.sql 로 초기화한 뒤 앱을 실행한다.
# 컨테이너가 재시작되면 데이터는 init.sql 상태로 되돌아간다.
set -e
mkdir -p /run/mysqld /var/lib/mysql
chown -R mysql:mysql /run/mysqld /var/lib/mysql
mariadb-install-db --user=mysql --datadir=/var/lib/mysql --skip-test-db >/dev/null

mariadbd --user=mysql --bind-address=127.0.0.1 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci \
  --innodb-buffer-pool-size=32M --performance-schema=OFF --max-connections=30 &

echo "[entrypoint] waiting for MariaDB..."
for i in $(seq 1 60); do
  if mariadb --protocol=socket -uroot -e "SELECT 1" >/dev/null 2>&1; then
    echo "[entrypoint] MariaDB is ready"
    break
  fi
  sleep 1
done

echo "[entrypoint] initializing database"
mariadb --protocol=socket -uroot -e "CREATE DATABASE fuelsync CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fuelsync'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON fuelsync.* TO 'fuelsync'@'localhost';"
mariadb --protocol=socket -uroot --default-character-set=utf8mb4 fuelsync < /app/init.sql
echo "[entrypoint] database initialized, starting app"

exec java -Xmx256m -jar /app/app.war
