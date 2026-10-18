(ns router-loop.consolidation-experiment
  "Ben's directive 2026-10-17: 'Wire consolidation next.'

   Diagnosis (results/learning-diagnosis.md) proved the 6
   :ontology/consolidation-requested events in the store were orphaned
   because ai.obney.orc.ontology.core.consolidator — home of the
   :ontology/consolidate-on-request defprocessor — was never loaded:
   Grain registers defprocessors as a namespace-load side effect, and
   neither ORC's ontology interface nor this consumer required it.

   The wiring (consumer/src/router_loop/system.clj, this same change):
   (1) system.clj now requires the consolidator namespace, so the
       processor registers and starts with the system;
   (2) the ctx now pins :ontology-consolidator-model to gpt-5.6-luna —
       the exact key consolidator.clj reads first when building its
       reflection :llm node ((or (:ontology-consolidator-model ctx)
       (:model ctx))); the ctx previously carried neither key.

   THE DISTINGUISHING EXPERIMENT, as specified in the diagnosis:
   Phase OBSERVE — start the wired system and watch whether the newly
     loaded processor picks up the 6 historical requests on its own
     (startup/replay) or ignores them (pubsub only sees new events).
   Phase FIRE — dispatch ONE new on-demand :ontology/request-consolidation
     for :node-type :leaf (richest history: 3 orphans, delta counter,
     today's judged executions). The processor's handler first sweeps
     same-target orphans past the 30-min grace into durable
     :ontology/description-consolidation-failed (:caller-interrupted)
     records, then runs the reflection.
   Phase SETTLE — poll (5s x 12 rounds, extend once on activity) until
     the new request has a durable outcome in the
     :ontology/consolidation-request-ledger, then report per-request
     dispositions for all 7 requests via
     orm/get-consolidation-outcome, the leaf description before/after,
     and the LLM spend read from event provenance.

   Any terminal outcome — completed / failed / skipped — is a valid
   result: the point is the chain now ANSWERS. Nothing here touches
   ORC sources; all targets are ORC seed scaffolding (no personal data)."
  (:require [router-loop.system :as sys]
            [ai.obney.grain.todo-processor-v2.interface :as tp]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.time.interface :as time]
            [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.orc.ontology.core.read-models :as orm]))

(def poll-interval-ms 5000)
(def poll-max-rounds 12)

(def outcome-types
  #{:ontology/node-type-description-updated
    :ontology/node-instance-description-updated
    :ontology/tree-description-updated
    :ontology/tree-class-description-updated
    :ontology/description-consolidation-failed
    :ontology/consolidation-skipped
    :ontology/claim-deltas-recorded})

(def baseline-orphan-ids
  "The 6 requests orphaned before this wiring (from results/loop_state.json
   @ acbc4fd): leaf x3 then node-instance [smoke-a sheet, summarize node] x3."
  ["01a14b06-13a8-70b3-b77f-b68990ad3e96"
   "01a14b08-3253-70e7-a984-66ba6eaf13f4"
   "01a14b0a-265e-7046-9780-ce40e18d6a35"
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

(defn- leaf-description-snapshot [ctx]
  (let [d (ontology/get-description ctx :node-type :leaf)]
    {:present? (some? d)
     :version (:version d)
     :claims (count (:claims d))
     :consolidated-from-event-count (:consolidated-from-event-count d)
     :summary (truncate (:summary d) 200)
     :capabilities (count (:capabilities d))
     :strengths (count (:strengths d))
     :weaknesses (count (:weaknesses d))
     :history-count (try (count (ontology/get-description-history ctx :node-type :leaf))
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
  "Node-execution-completed events carrying a :model — in this run the
   only sheet executions are the consolidator's reflections."
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
            leaf-before (leaf-description-snapshot ctx)
            unanswered-before (ontology/get-unanswered-consolidation-requests ctx {})]
        (println "BASELINE:" (pr-str baseline))
        (println "UNANSWERED-BEFORE:" (pr-str (mapv :request-id unanswered-before)))
        (println "LEAF-DESC-BEFORE:" (pr-str leaf-before))

        ;; ---- Phase OBSERVE: do the orphans get picked up at startup?
        (let [observe
              (poll-phase
                "OBSERVE"
                (fn []
                  (let [snap (outcome-snapshot ctx)]
                    {:done? false ;; always observe the full window
                     :activity? (not= snap baseline)
                     :snapshot snap})))
              observed-snap (:snapshot observe)]
          (println "OBSERVE-RESULT:"
                   (pr-str {:changed? (not= observed-snap baseline)
                            :extended? (:extended? observe)
                            :final observed-snap}))

          ;; ---- Phase FIRE: one on-demand request for :node-type :leaf
          (cp/process-command
            (assoc ctx :command
                   {:command/name :ontology/request-consolidation
                    :command/id (random-uuid)
                    :command/timestamp (time/now)
                    :target-type :node-type
                    :target-id :leaf
                    :on-demand? true}))
          (Thread/sleep 1000)
          (let [new-requests (remove #(contains? (set baseline-orphan-ids) (str (:event/id %)))
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
                                        (not= (:unanswered snap) (:unanswered observed-snap)))
                         :snapshot {:outcome-status (:status outcome)
                                    :outcome-events (:events outcome)
                                    :counts snap}})))]
              (println "SETTLE-RESULT:" (pr-str {:rounds (:rounds settle)
                                                  :extended? (:extended? settle)
                                                  :snapshot (:snapshot settle)})))

            ;; ---- Final accounting -----------------------------------
            (let [final-snap (outcome-snapshot ctx)
                  leaf-after (leaf-description-snapshot ctx)
                  unanswered-after (ontology/get-unanswered-consolidation-requests ctx {})
                  all-ids (conj baseline-orphan-ids new-id)
                  dispositions
                  (mapv (fn [rid]
                          (let [o (outcome-for ctx rid)]
                            {:request-id rid
                             :status (:status o)
                             :events (:events o)}))
                        all-ids)
                  refl (reflection-activity ctx)
                  usage-total (reduce + 0 (keep #(get-in % [:usage :total-tokens]) refl))]
              (println "FINAL:" (pr-str final-snap))
              (println "UNANSWERED-AFTER:" (pr-str (mapv :request-id unanswered-after)))
              (println "DISPOSITIONS:")
              (doseq [d dispositions] (println " " (pr-str d)))
              (println "LEAF-DESC-AFTER:" (pr-str leaf-after))
              (println "REFLECTION-EXECUTIONS:" (count refl))
              (doseq [e refl]
                (println " " (pr-str {:sheet-id (:sheet-id e)
                                       :node-id (:node-id e)
                                       :model (:model e)
                                       :usage (:usage e)
                                       :at (:event/timestamp e)})))
              (println "USAGE-TOTAL-TOKENS:" usage-total)
              (println "SUMMARY:"
                       (pr-str {:baseline baseline
                                :observed observed-snap
                                :new-request-id new-id
                                :final final-snap
                                :leaf-before leaf-before
                                :leaf-after leaf-after
                                :dispositions dispositions
                                :reflection-executions (count refl)
                                :usage-total-tokens usage-total}))))))
      (finally (sys/stop sys))))
  (shutdown-agents)
  (System/exit 0))
