(defproject scylla "0.1.0-SNAPSHOT"
  :description "kubernetes PaaS"
  :url "https://github.com/DreamHostData/scylla"
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [figwheel-sidecar "0.5.0-SNAPSHOT" :scope "test"]
                                  [devcards "0.2.1-7"]
                                  [reloaded.repl "0.2.1"]]}}
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :creds :gpg}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.omcljs/om "1.0.0-alpha34"]
                 [org.clojure/core.async "0.2.374"]
                 [com.datomic/datomic-pro "0.9.5359"]
                 [org.clojars.jds02006/kubernetes-api "0.1.1-SNAPSHOT"]
                 [cljs-http "0.1.41"]
                 [org.apache.httpcomponents/httpclient "4.5.1"]
                 [environ "1.0.3"]
                 [oauth-clj "0.1.15"]
                 [http-kit "2.1.18"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [manifold "0.1.4"]
                 [aleph "0.4.2-alpha3"]
                 [less-awful-ssl "1.0.1"]
                 [com.cemerick/url "0.1.1"]
                 [byte-streams "0.2.2"]
                 [cheshire "5.6.1"]
                 [juxt/dirwatch "0.2.3"]
                 [camel-snake-kebab "0.4.0"]
                 [bidi "2.0.9"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/sente "1.9.0-beta3"]
                 [com.taoensso/timbre "4.3.1"]
                 [datomic-schema "1.3.0"]])
