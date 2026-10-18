(ns router-loop.bridge
  "Socket-layer bridge for litellm's HTTP calls (see scripts/http_bridge.py
   for the why). Redefines hato.client/post — the single call litellm's
   OpenAI provider makes — to delegate the request to the python helper
   over stdio and return a CompletableFuture of the hato-shaped response
   map, exactly as hato's :async? true path does. Everything above the
   socket (ORC, litellm routing/transforms, judges, GEPA) is untouched."
  (:require [cheshire.core :as json]
            [hato.client :as hato])
  (:import [java.util.concurrent CompletableFuture]
           [java.io File]))

(def bridge-script
  (str (System/getProperty "user.dir") "/../scripts/http_bridge.py"))

(defn bridge-post
  "Drop-in replacement for hato.client/post for POSTs to the cliproxy
   tunnel base URL. Returns a CompletableFuture of {:status :headers :body}
   with :body parsed from JSON with keyword keys (hato :as :json shape)."
  [url opts]
  (let [payload (json/generate-string
                 {:url url
                  :headers (:headers opts)
                  :body (:body opts)})
        pb (ProcessBuilder. ["python3" bridge-script])
        _ (.redirectError pb java.lang.ProcessBuilder$Redirect/INHERIT)
        proc (.start pb)
        in (.getOutputStream proc)
        out (.getInputStream proc)]
    (.write in (.getBytes payload "UTF-8"))
    (.close in)
    (let [resp-text (slurp (java.io.InputStreamReader. out "UTF-8"))
          _ (.waitFor proc)
          resp (json/parse-string resp-text true)]
      (when (zero? (:status resp))
        (throw (ex-info (str "http-bridge transport failed: " (:error resp))
                        {:url url})))
      (CompletableFuture/completedFuture
       {:status (:status resp)
        :headers {}
        :body (json/parse-string (:body resp) true)}))))

(defn install!
  "Install the bridge as hato.client/post's root binding for this process."
  []
  (alter-var-root #'hato/post (constantly bridge-post))
  (println "http bridge installed: hato.client/post -> scripts/http_bridge.py"))
