FROM clojure:lein-2.6.1-alpine
COPY project /opt/project
WORKDIR /opt/project
RUN lein install
