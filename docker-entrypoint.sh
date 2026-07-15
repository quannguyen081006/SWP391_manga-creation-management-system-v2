#!/bin/sh
set -e

# Overwrite the baked-in dev jdbc.properties (localhost/SA/12345) with real
# connection info from environment variables, if provided. Set these in the
# Render service's Environment tab:
#   JDBC_URL      e.g. jdbc:sqlserver://<oracle-vm-public-ip>:1433;databaseName=MangaEditorialDB;encrypt=true;trustServerCertificate=true
#   JDBC_USERNAME e.g. SA (or a dedicated app user)
#   JDBC_PASSWORD
CONF_FILE=/usr/local/tomcat/webapps/MangaProject/WEB-INF/jdbc.properties

if [ -n "$JDBC_URL" ]; then
  cat > "$CONF_FILE" <<EOF
jdbc.driverClassName=${JDBC_DRIVER_CLASS_NAME:-com.microsoft.sqlserver.jdbc.SQLServerDriver}
jdbc.url=${JDBC_URL}
jdbc.username=${JDBC_USERNAME}
jdbc.password=${JDBC_PASSWORD}
EOF
fi

exec "$@"
