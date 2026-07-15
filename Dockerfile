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

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["catalina.sh", "run"]
