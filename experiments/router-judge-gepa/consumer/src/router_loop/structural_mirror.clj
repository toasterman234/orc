(ns router-loop.structural-mirror
  "Phase 1 of the FIT follow-through (Ben's 2026-10-17 ORC-native
   decision, PLAN.md §8): mirror the master-repo-0.1 Structural-v1 class
   vocabulary into a NEW ontology in the consumer's local store, the
   ORC-native way — 41 rich concepts + one Living Description each,
   edges only where the source schema declares them.

   The payload (concepts / descriptions / edges, with provenance stamps)
   is produced deterministically by src/build_structural_mirror.py from
   the pinned schema copy in data/ and read from
   data/structural-mirror/payload.json. NO LLM calls happen in this
   namespace: authoring was the deterministic transform; this runner
   only issues ontology lifecycle commands (create-ontology,
   create-concept, record-tree-class-description, create-relationship)
   and then reads everything back for verification.

   Idempotent by construction: ontology/concept/relationship ids are
   deterministic (uuid5 of the URIs), create commands carry them as
   :command/id (the handlers' own idempotency seam), and descriptions
   are skipped when a body already exists for the target.

   Modes: apply (default) | verify (read-only re-check, no commands)."
  (:require [router-loop.system :as sys]
            [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.time.interface :as time]
            [ai.obney.grain.event-store-v3.interface :as es]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.util UUID]))

(def data-dir (str (System/getProperty "user.dir") "/../data"))
(def payload-file (str data-dir "/structural-mirror/payload.json"))
(def verify-file (str data-dir "/structural-mirror/verify.json"))

(defn- load-payload []
  (json/parse-string (slurp payload-file) true))

(defn- anomaly? [result]
  (and (map? result) (some? (:cognitect.anomalies/category result))))

(defn- wait-for
  "Poll pred every 250 ms until truthy or timeout; returns pred's value."
  [label pred timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (let [v (try (pred) (catch Throwable _ nil))]
        (cond
          v (do (println "READY:" label) v)
          (> (System/currentTimeMillis) deadline)
          (throw (ex-info (str "timeout waiting for " label) {:label label}))
          :else (do (Thread/sleep 250) (recur)))))))

(defn- body-of [desc]
  ;; JSON keys arrive keywordized with dashes where the payload used
  ;; dashes already (consolidated-from-event-count, representative-uses,
  ;; avoid-when) — the description-body schema's exact key names.
  (:body desc))

(defn apply-mirror! [ctx payload]
  (let [ont (:ontology payload)
        oid (UUID/fromString (:ontology_id ont))
        concepts (:concepts payload)
        descriptions (:descriptions payload)
        relationships (:relationships payload)]
    ;; --- ontology (idempotent: the handler returns the existing
    ;; ontology when the command id is already an ontology id) ---
    (if (ontology/ontology-exists? ctx oid)
      (println "ontology already present:" oid)
      (let [res (ontology/create-ontology!
                 ctx {:command/id oid
                      :name (:name ont)
                      :scope (keyword (:scope ont))
                      :description (:description ont)
                      :base-uri (:base_uri ont)})]
        (when (anomaly? res)
          (throw (ex-info "create-ontology failed" {:result res})))
        (println "ontology created:" (pr-str res))))
    (wait-for "ontology visible in read model"
              #(ontology/ontology-exists? ctx oid) 60000)
    ;; --- concepts ---
    (doseq [c concepts]
      (let [cid (UUID/fromString (:concept_id c))]
        (if (ontology/get-concept-by-uri ctx oid (:uri c))
          (println "concept already present:" (:uri c))
          (let [res (ontology/create-concept!
                     ctx oid
                     {:command/id cid
                      :uri (:uri c)
                      :label (:label c)
                      :description (:description c)
                      :scope (keyword (:scope c))
                      :broader []
                      :indicators []
                      :provenance {:kind (keyword (:kind (:provenance c)))
                                   :source-reference (:source_reference (:provenance c))
                                   :created-by (:created_by (:provenance c))}})]
            (when (anomaly? res)
              (throw (ex-info (str "create-concept failed for " (:uri c))
                              {:result res})))
            (println "concept created:" (:uri c))))))
    (wait-for (str (count concepts) " concepts visible under the ontology")
              #(let [cs (ontology/get-concepts ctx {:ontology-id oid})]
                 (when (= (count concepts) (count cs)) cs))
              120000)
    ;; --- living descriptions (tree-class granularity, target = concept
    ;; id — the same keying ORC's own seed tree-classes use) ---
    (doseq [d descriptions]
      (let [tid (UUID/fromString (:target_id d))]
        (if (ontology/get-description ctx :tree-class tid)
          (println "description already present for" (:class d))
          (let [res (cp/process-command
                     (assoc ctx :command
                            {:command/name :ontology/record-tree-class-description
                             :command/id (random-uuid)
                             :command/timestamp (time/now)
                             :target-id tid
                             :body (body-of d)}))]
            (when (anomaly? res)
              (throw (ex-info (str "record-description failed for " (:class d))
                              {:result res})))
            (println "description recorded for" (:class d))))))
    (wait-for "all descriptions readable"
              #(let [found (count (filter (fn [d]
                                            (ontology/get-description
                                             ctx :tree-class
                                             (UUID/fromString (:target_id d))))
                                          descriptions))]
                 (when (= (count descriptions) found) found))
              120000)
    ;; --- relationships. Skip any whose (source,target,predicate)
    ;; tuple is already in the event stream (the handler's own
    ;; duplicate check would refuse it as a conflict; existence in the
    ;; stream IS the success condition). ORC projects skos:related
    ;; symmetrically, so the payload stores bidirectional pairs once —
    ;; see build_structural_mirror.py. ---
    (let [existing (into #{}
                         (comp (filter #(= :ontology/relationship-created (:event/type %)))
                               (map (fn [e] [(:source-ontology-id e)
                                             (:source-uri e)
                                             (:target-uri e)
                                             (:predicate e)])))
                         (es/read (:event-store ctx) {:tenant-id sys/tenant-id}))
          counts (reduce
                  (fn [acc r]
                    (let [tuple [oid (:source_uri r) (:target_uri r) (:predicate r)]]
                      (if (contains? existing tuple)
                        (update acc :already-present inc)
                        (let [res (ontology/create-relationship!
                                   ctx {:command/id (UUID/fromString (:relationship_id r))
                                        :source-ontology-id oid
                                        :target-ontology-id oid
                                        :source-uri (:source_uri r)
                                        :target-uri (:target_uri r)
                                        :predicate (:predicate r)
                                        :metadata {:source-reference (:source_reference r)}})]
                          (when (anomaly? res)
                            (throw (ex-info (str "create-relationship failed: "
                                                 (:source_uri r) " -> " (:target_uri r))
                                            {:result res})))
                          (update acc :created inc)))))
                  {:created 0 :already-present 0}
                  relationships)]
      (println "relationships:" (pr-str counts) "of" (count relationships)))
    {:ontology-id oid}))

(defn verify! [ctx payload]
  (let [oid (UUID/fromString (:ontology_id (:ontology payload)))
        ont (ontology/get-ontology ctx oid)
        concepts (ontology/get-concepts ctx {:ontology-id oid})
        all-concepts (ontology/get-concepts ctx)
        events (into [] (es/read (:event-store ctx) {:tenant-id sys/tenant-id}))
        our-rel-events (filter #(and (= :ontology/relationship-created (:event/type %))
                                     (= oid (:source-ontology-id %)))
                               events)
        our-concept-events (filter #(and (= :ontology/concept-created (:event/type %))
                                         (= oid (:ontology-id %)))
                                   events)
        desc-check (mapv (fn [d]
                           (let [tid (UUID/fromString (:target_id d))
                                 body (ontology/get-description ctx :tree-class tid)
                                 hist (ontology/get-description-history ctx :tree-class tid)
                                 claims (ontology/get-claims ctx :tree-class tid)]
                             {:class (:class d)
                              :present (some? body)
                              :history-versions (count hist)
                              :claims (count claims)
                              :summary (:summary body)}))
                         (:descriptions payload))
        sample-uris ["urn:master-repo-0.1:structural-v1:Goal"
                     "urn:master-repo-0.1:structural-v1:GovernedClassifier"
                     "urn:master-repo-0.1:structural-v1:Relationship"]
        samples (mapv (fn [u]
                        (let [c (ontology/get-concept-by-uri ctx oid u)]
                          {:uri (:uri c)
                           :id (str (:id c))
                           :label (:label c)
                           :scope (str (:scope c))
                           :ontology-id (str (:ontology-id c))
                           :description (:description c)
                           :provenance (:provenance c)
                           :broader (:broader c)
                           :narrower-count (count (:narrower c))
                           :related-count (count (:related c))}))
                      sample-uris)
        goal-desc (first (filter #(= "Goal" (:class %)) desc-check))
        report {:ontology (when ont
                            {:id (str (:ontology-id ont))
                             :name (:name ont)
                             :scope (str (:scope ont))
                             :base-uri (:base-uri ont)
                             :description (:description ont)})
                :counts {:concepts-under-ontology (count concepts)
                         :concepts-total-in-store (count all-concepts)
                         :concept-created-events-for-ontology (count our-concept-events)
                         :relationship-created-events-for-ontology (count our-rel-events)
                         :descriptions-present (count (filter :present desc-check))
                         :descriptions-total (count desc-check)}
                :statistics (ontology/concept-statistics ctx)
                :concept-samples samples
                :description-sample (when goal-desc
                                      {:class "Goal"
                                       :history-versions (:history-versions goal-desc)
                                       :claims (:claims goal-desc)
                                       :summary (:summary goal-desc)})
                :per-class-description-check (mapv #(dissoc % :summary) desc-check)}]
    (spit verify-file (json/generate-string report {:pretty true}))
    (println "VERIFY counts:" (pr-str (:counts report)))
    (println "VERIFY statistics:" (pr-str (:statistics report)))
    (println "VERIFY ontology:" (pr-str (:ontology report)))
    (println "saved" verify-file)
    report))

(defn -main [& [mode]]
  (let [payload (load-payload)
        sys (sys/start)
        ctx (:ctx sys)]
    (try
      (when (not= mode "verify")
        (apply-mirror! ctx payload)
        (Thread/sleep 1000))
      (verify! ctx payload)
      (finally (sys/stop sys))))
  (shutdown-agents)
  (System/exit 0))
