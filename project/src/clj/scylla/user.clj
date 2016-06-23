(ns scylla.user)

(defn namespace-for [user]
  (str "user-" (:username user)))

(defn ssl-certs-for [user host]
  (format "/etc/letsencrypt/live/%s/fullchain.pem" host))

(defn ssl-key-for [user host]
  (format "/etc/letsencrypt/live/%s/privkey.pem" host))
