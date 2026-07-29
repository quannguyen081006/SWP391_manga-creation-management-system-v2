# Builds and runs MangaProject (Java 8 / Spring MVC / JSP) on Tomcat 9.
# Compiles with plain javac (not Ant) because this NetBeans project's Ant build
# references NetBeans-global library definitions that don't exist outside the
# original dev machine. All runtime jars are checked into web/WEB-INF/lib.
FROM tomcat:9-jdk8-temurin

# Drop the sample Tomcat webapps we don't need.
RUN rm -rf /usr/local/tomcat/webapps/ROOT \
           /usr/local/tomcat/webapps/docs \
           /usr/local/tomcat/webapps/examples \
           /usr/local/tomcat/webapps/host-manager \
           /usr/local/tomcat/webapps/manager

# Disable Tomcat's shutdown port. In a container it is useless (Render stops the
# service with SIGTERM), it accepts an unauthenticated SHUTDOWN that would kill
# the app, and leaving it open makes the platform's port scan find 8005 as well
# as 8080 — health checks then land on a socket that speaks no HTTP.
RUN sed -i 's/<Server port="8005"/<Server port="-1"/' /usr/local/tomcat/conf/server.xml \
    && grep -q '<Server port="-1"' /usr/local/tomcat/conf/server.xml

WORKDIR /build
COPY src/java ./src/java
COPY web ./webapp
COPY src/conf/jdbc.properties ./webapp/WEB-INF/jdbc.properties

RUN mkdir -p ./webapp/WEB-INF/classes \
    && CP="$(find ./webapp/WEB-INF/lib /usr/local/tomcat/lib -name '*.jar' | tr '\n' ':')" \
    && find ./src/java -name '*.java' > sources.txt \
    && javac -source 8 -target 8 -encoding UTF-8 -cp "$CP" -d ./webapp/WEB-INF/classes @sources.txt \
    && rm -rf /usr/local/tomcat/webapps/MangaProject \
    && mv ./webapp /usr/local/tomcat/webapps/MangaProject

# Serve something at "/". The app lives under /MangaProject, so the root path
# returned 404 and any health check pointed at "/" marked the instance
# unhealthy. This page answers 200 (health check passes) and bounces a real
# browser on to the login screen, so the deploy no longer depends on the
# Health Check Path being configured in the Render dashboard.
RUN mkdir -p /usr/local/tomcat/webapps/ROOT \
    && printf '%s\n' \
        '<!doctype html>' \
        '<html lang="en">' \
        '<head>' \
        '<meta charset="utf-8">' \
        '<title>MangaProject</title>' \
        '<meta http-equiv="refresh" content="0; url=/MangaProject/main/login">' \
        '</head>' \
        '<body>Redirecting to <a href="/MangaProject/main/login">MangaProject</a>&hellip;</body>' \
        '</html>' \
        > /usr/local/tomcat/webapps/ROOT/index.html

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Keep the JVM inside the 512MB the Render Starter plan allows. Without a
# Metaspace cap the container was killed with status 137 (OOM) every few
# minutes: JSPs are compiled at runtime, so each page loads new classes that
# Metaspace never releases, and it grows unbounded by default.
# Heap 256m + Metaspace 128m + stacks/native leaves headroom under the limit.
# SerialGC is deliberate — G1's bookkeeping is not worth it at this heap size.
ENV CATALINA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseSerialGC"

EXPOSE 8080
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["catalina.sh", "run"]
