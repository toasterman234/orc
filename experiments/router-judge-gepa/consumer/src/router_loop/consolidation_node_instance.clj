(ns router-loop.consolidation-node-instance
  "Ben's directive 2026-10-17: 'Answer the 3 node-instance orphans too.'

   Follow-on to results/consolidation-wiring.md (the leaf run). The
   consolidator is now wired in router-loop.system (namespace loaded,
   :ontology-consolidator-model pinned to gpt-5.6-luna), but its orphan
   sweep is scoped to the target of the request being answered — so the
   3 historical :ontology/consolidation-requested events for the smoke-a
   summarizer NODE-INSTANCE target are still pending:

     01a14b06-140b… / 01a14b08-3292… / 01a14b0a-26e7…
     target-type :node-instance
     target-id   [dd9e3bce-16e4-56ed-bdcd-e32c08852a4b   (smoke-a sheet)
                  90dcc7a5-cd9a-5bc7-b7fa-755090c1d75f]  (summarize node)

   This driver fires ONE new on-demand :ontology/request-consolidation
   for that exact target. Expected shape (verified against the leaf
   run, not assumed): the handler sweeps the 3 same-target orphans into
   :ontology/description-consolidation-failed (:caller-interrupted,
   attempts 0), then the reflection consolidates the instance and the
   outcome lands as :ontology/node-instance-description-updated.

   Phases: BASELINE (target description before-state) → FIRE → SETTLE
   (poll 5s x 12, extend once on activity) → FINAL accounting for all
   8 consolidation requests in the store (7 historical + the new one),
   the instance description after-state, and LLM spend from event
   provenance. No other target, no other request — this directive
   covers the node-instance orphans only."
  (:require [router-loop.system :as sys]
            [ai.obney.grain.todo-processor-v2.interface :as tp]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.time.interface :as time]
            [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.orc.ontology.core.read-models :as orm]))

(def poll-interval-ms 5000)
(def poll-max-rounds 12)

(def target-sheet-id (java.util.UUID/fromString "dd9e3bce-16e4-56ed-bdcd-e32c08852a4b"))
(def target-node-id (java.util.UUID/fromString "90dcc7a5-cd9a-5bc7-b7fa-755090c1d75f"))
(def target-id [target-sheet-id target-node-id])

(def baseline-request-ids
  "All 7 consolidation requests in the store before this run
   (results/consolidation-wiring.md disposition table): leaf x3
   (swept-failed), leaf on-demand (completed), node-instance x3
   (still pending — this run's subject)."
  ["01a14b06-13a8-70b3-b77f-b68990ad3e96"
   "01a14b08-3253-70e7-a984-66ba6eaf13f4"
   "01a14b0a-265e-7046-9780-ce40e18d6a35"
   "01a14c7a-cc7a-7066-9a8b-2ccd506e0ad1"
   "01a14b06-140b-70ab-b2ae-8b033cfa6762"
   "01a14b08-3292-7073-8272-d56cedc5c2c5"
   "01a14b0a-26e7-700d-b46d-e993c6657cf7"])

(defn- events-of [ctx types]
  (into [] (es/read (:event-store ctx)
                    {:types types :tenant-id (:tenant-id ctx)})))

(defn- count-type [ctx t]
  (count (events-of ctx #{t})))

(defn- outcome-snapshot [ctx]
  {:unanswered (count (ontology/get-unanswered-consolidation-requests ctx {}))
   :requested (count-type ctx :ontology/consolidation-requested)
   :failed (count-type ctx :ontology/description-consolidation-failed)
   :skipped (count-type ctx :ontology/consolidation-skipped)
   :claim-deltas (count-type ctx :ontology/claim-deltas-recorded)
   :node-type-desc-updated (count-type ctx :ontology/node-type-description-updated)
   :node-instance-desc-updated (count-type ctx :ontology/node-instance-description-updated)
   :tree-desc-updated (count-type ctx :ontology/tree-description-updated)
   :node-exec-completed (count-type ctx :sheet/node-execution-completed)})

(defn- truncate [s n]
  (let [s (str s)] (if (> (count s) n) (str (subs s 0 n) "…") s)))

(defn- instance-description-snapshot [ctx]
  (let [d (ontology/get-description ctx :node-instance target-id)]
    {:present? (some? d)
     :version (:version d)
     :claims (count (:claims d))
     :consolidated-from-event-count (:consolidated-from-event-count d)
     :summary (truncate (:summary d) 200)
     :capabilities (count (:capabilities d))
     :strengths (count (:strengths d))
     :weaknesses (count (:weaknesses d))
     :history-count (try (count (ontology/get-description-history ctx :node-instance target-id))
                         (catch Exception e (str "ERR " (.getMessage e))))}))

(defn- poll-phase
  "Poll check-fn every 5s for up to poll-max-rounds rounds. check-fn
   returns {:done? bool :activity? bool :snapshot any}. Extends ONCE by
   another poll-max-rounds rounds if not done but the last check showed
   activity. Prints every round; returns the final check map + rounds."
  [label check-fn]
  (loop [round 0
         extended? false]
    (let [{:keys [done? activity?] :as chk} (check-fn)
          _ (println label "round" round (pr-str (:snapshot chk)))]
      (cond
        done? (assoc chk :rounds round :extended? extended?)
        (and (>= round (dec poll-max-rounds)) (not extended?) activity?)
        (do (println label "EXTEND once — activity without terminal outcome")
            (recur (inc round) true))
        (>= round (dec (* poll-max-rounds (if extended? 2 1))))
        (assoc chk :rounds round :extended? extended?)
        :else (do (Thread/sleep poll-interval-ms)
                  (recur (inc round) extended?))))))

(defn- outcome-for
  "orm/get-consolidation-outcome keyed by request-id; the ledger's key
   type (UUID vs string) is an implementation detail — try both."
  [ctx rid-str]
  (let [as-uuid (try (java.util.UUID/fromString rid-str) (catch Exception _ nil))
        o1 (when as-uuid (orm/get-consolidation-outcome ctx as-uuid))
        o2 (orm/get-consolidation-outcome ctx rid-str)]
    (if (and o1 (not= :none (:status o1))) o1 o2)))

(defn- reflection-activity
  "Node-execution-completed events carrying a :model — the
   consolidator's reflections (plus, historically, the leaf run's one)."
  [ctx]
  (filter :model (events-of ctx #{:sheet/node-execution-completed})))

(defn -main [& _]
  (let [sys (sys/start)
        ctx (:ctx sys)]
    (try
      ;; ---- Wiring verification -------------------------------------
      (let [registered (set (keys @tp/processor-registry*))]
        (println "PROCESSORS-REGISTERED:" (count registered))
        (println "PROCESSORS-STARTED:" (count (:processors sys)))
        (println "CONSOLIDATOR-REGISTERED?"
                 (contains? registered :ontology/consolidate-on-request)))
      (println "MODEL-PIN-READBACK:"
               (pr-str {:ontology-consolidator-model (:ontology-consolidator-model ctx)
                        :model (:model ctx)}))
      (println "LD-ENABLED-READBACK:"
               (ontology/get-living-description-enabled? ctx))

      ;; ---- Baseline -------------------------------------------------
      (let [baseline (outcome-snapshot ctx)
            desc-before (instance-description-snapshot ctx)
            unanswered-before (ontology/get-unanswered-consolidation-requests ctx {})
            refl-before-ids (set (map #(str (:event/id %)) (reflection-activity ctx)))]
        (println "BASELINE:" (pr-str baseline))
        (println "UNANSWERED-BEFORE:" (pr-str (mapv :request-id unanswered-before)))
        (println "INSTANCE-DESC-BEFORE:" (pr-str desc-before))
        (println "REFLECTION-EXECS-BEFORE:" (count refl-before-ids))

        ;; ---- Phase FIRE: one on-demand request for the node-instance
        (cp/process-command
          (assoc ctx :command
                 {:command/name :ontology/request-consolidation
                  :command/id (random-uuid)
                  :command/timestamp (time/now)
                  :target-type :node-instance
                  :target-id target-id
                  :on-demand? true}))
        (Thread/sleep 1000)
        (let [new-requests (remove #(contains? (set baseline-request-ids) (str (:event/id %)))
                                   (events-of ctx #{:ontology/consolidation-requested}))
              new-request (last (sort-by :event/timestamp new-requests))
              new-id (some-> new-request :event/id str)
              execs-at-fire (count (reflection-activity ctx))]
          (println "FIRED:" (pr-str {:new-request-id new-id
                                      :event (select-keys new-request [:target-type :target-id :on-demand? :event/timestamp])}))

          ;; ---- Phase SETTLE: wait for the new request's outcome ----
          (let [settle
                (poll-phase
                  "SETTLE"
                  (fn []
                    (let [outcome (when new-id (outcome-for ctx new-id))
                          snap (outcome-snapshot ctx)]
                      {:done? (and outcome (not= :none (:status outcome)))
                       :activity? (or (> (count (reflection-activity ctx)) execs-at-fire)
                                      (not= (:unanswered snap) (:unanswered baseline)))
                       :snapshot {:outcome-status (:status outcome)
                                  :outcome-events (:events outcome)
                                  :counts snap}})))]
            (println "SETTLE-RESULT:" (pr-str {:rounds (:rounds settle)
                                                :extended? (:extended? settle)
                                                :snapshot (:snapshot settle)})))

          ;; ---- Final accounting -----------------------------------
          (let [final-snap (outcome-snapshot ctx)
                desc-after (instance-description-snapshot ctx)
                unanswered-after (ontology/get-unanswered-consolidation-requests ctx {})
                all-ids (conj baseline-request-ids new-id)
                dispositions
                (mapv (fn [rid]
                        (let [o (outcome-for ctx rid)]
                          {:request-id rid
                           :status (:status o)
                           :events (:events o)}))
                      all-ids)
                refl-new (remove #(contains? refl-before-ids (str (:event/id %)))
                                 (reflection-activity ctx))
                usage-total (reduce + 0 (keep #(get-in % [:usage :total-tokens]) refl-new))]
            (println "FINAL:" (pr-str final-snap))
            (println "UNANSWERED-AFTER:" (pr-str (mapv :request-id unanswered-after)))
            (println "DISPOSITIONS:")
            (doseq [d dispositions] (println " " (pr-str d)))
            (println "INSTANCE-DESC-AFTER:" (pr-str desc-after))
            (println "REFLECTION-EXECUTIONS-THIS-RUN:" (count refl-new))
            (doseq [e refl-new]
              (println " " (pr-str {:sheet-id (:sheet-id e)
                                     :node-id (:node-id e)
                                     :model (:model e)
                                     :usage (:usage e)
                                     :at (:event/timestamp e)})))
            (println "USAGE-TOTAL-TOKENS-THIS-RUN:" usage-total)
            (println "SUMMARY:"
                     (pr-str {:baseline baseline
                              :new-request-id new-id
                              :final final-snap
                              :desc-before desc-before
                              :desc-after desc-after
                              :dispositions dispositions
                              :reflection-executions-this-run (count refl-new)
                              :usage-total-tokens-this-run usage-total})))))
      (finally (sys/stop sys))))
  (shutdown-agents)
  (System/exit 0))
