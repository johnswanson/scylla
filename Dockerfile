FROM clojure:lein-2.6.1-alpine
MAINTAINER John Swanson "john.d.swanson@gmail.com"
ENV DATOMIC_VERSION 0.9.5359
ENV DATOMIC_HOME /opt/datomic-pro-$DATOMIC_VERSION

RUN mkdir /opt
ADD datomic/.credentials /tmp/
RUN apk --no-cache add curl && apk --no-cache --repository http://nl.alpinelinux.org/alpine/edge/testing add maven
RUN curl -u $(cat /tmp/.credentials) -SL https://my.datomic.com/repo/com/datomic/datomic-pro/$DATOMIC_VERSION/datomic-pro-$DATOMIC_VERSION.zip -o /tmp/datomic.zip \
  && unzip /tmp/datomic.zip -d /opt \
  && rm -f /tmp/datomic.zip \
	&& cd $DATOMIC_HOME && ./bin/maven-install
WORKDIR /scylla

COPY project/project.clj /scylla/project.clj
RUN lein deps

COPY project /scylla
ENV LEIN_REPL_HOST=0.0.0.0
CMD lein repl :headless :host 0.0.0.0 :port 4096
