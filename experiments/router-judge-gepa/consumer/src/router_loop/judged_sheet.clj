(ns router-loop.judged-sheet
  "Part B (Ben's directive, 2026-10-17): make judge scores PERSIST.

   Every judge call in this install before this namespace used the OFFLINE
   path — `eval/evaluate-single` (evaluation/core/judges.clj), a pure
   in-memory function with no ctx / event store / command dispatch — so no
   `:judge/score-emitted` event could ever be written and the
   `:evaluation/judge-scores` read model stayed empty (diagnosis:
   results/learning-diagnosis.md).

   This namespace exercises the ATTACHED path exactly as ORC designs it:
   judges declared with `orc/judges` on the sheet and attached to the node
   with `:judges [...]` (docs/GETTING-STARTED.md Phase 2). On
   `:sheet/node-execution-completed`, the registered
   `:evaluation/on-node-execution-completed` todo-processor
   (evaluation/core/judge_runtime.clj) resolves the node's judges, runs
   them in a background future, and dispatches
   `:evaluation/record-judge-score` commands — the sole writers of
   `:judge/score-emitted` (evaluation/core/commands.clj) — which fold
   into the `:evaluation/judge-scores` read model.

   Subject: the smoke-a summarizer sheet + the same synthetic article
   (no personal data). Judges: tier-1 :grounding + :reasoning, configs
   pinned to the consumer provider/model (cliproxy / gpt-5.6-luna).

   The run also captures the downstream observation (Part B.4): judge-
   scores projection, tree profiles, node experiences, description
   claims and consolidation counters BEFORE and AFTER, printed as one
   EDN summary. Nothing here issues classification, record-tree-*,
   record-node-pattern or consolidation commands — downstream movement
   is observed, never forced."
  (:require [router-loop.system :as sys]
            [router-loop.smokes :as smokes]
            [ai.obney.orc.orc-service.interface :as orc]
            [ai.obney.orc.evaluation.interface :as eval]
            [ai.obney.orc.evaluation.core.judges :as judges]
            [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.orc.ontology.core.read-models :as orm]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]))

(def judged-summarizer
  (orc/workflow "summarizer-judged"
    (orc/blackboard {:input :string :summary :string})
    (orc/judges
      {:summary-grounding {:type :grounding
                           :weight 0.5
                           :provider sys/provider-kw
                           :model sys/model}
       :summary-reasoning  {:type :reasoning
                           :weight 0.5
                           :provider sys/provider-kw
                           :model sys/model}})
    (orc/sequence "main"
      (orc/llm "summarize"
        :model sys/model
        :instruction "Summarize the input text in 2 sentences."
        :reads [:input]
        :writes [:summary]
        :judges ["summary-grounding" "summary-reasoning"]))))

(def executions-planned 2)
(def poll-interval-ms 5000)
(def poll-max-rounds 12) ;; 12 x 5s = 60s max wait per poll phase

(defn- count-events [ctx types]
  (count (into [] (es/read (:event-store ctx)
                           {:types types :tenant-id (:tenant-id ctx)}))))

(defn- sheet-judge-entries
  "All :evaluation/judge-scores entries for one sheet, flattened with
   their [sheet tick node] keys."
  [ctx sheet-id]
  (let [state (rmp/project ctx :evaluation/judge-scores)]
    (vec
      (for [[[sid tick-id node-id] entries] state
            :when (= sid sheet-id)
            e entries]
        (assoc e :tick-id tick-id :node-id node-id)))))

(defn- downstream-snapshot [ctx]
  {:judge-score-events (count-events ctx #{:judge/score-emitted})
   :composite-events (count-events ctx #{:judge/composite-score-computed})
   :judge-scores-projection-total
   (reduce + 0 (map count (vals (rmp/project ctx :evaluation/judge-scores))))
   :tree-profiles (count (rmp/project ctx :ontology/tree-profiles))
   :node-experiences (count (rmp/project ctx :ontology/node-experiences))
   :claim-delta-events (count-events ctx #{:ontology/claim-deltas-recorded})
   :consolidation-requested (count-events ctx #{:ontology/consolidation-requested})
   :consolidation-skipped (count-events ctx #{:ontology/consolidation-skipped})
   :consolidation-failed (count-events ctx #{:ontology/description-consolidation-failed})
   :task-classified (count-events ctx #{:ontology/task-classified})
   :node-type-leaf-delta (orm/get-consolidation-delta ctx :node-type :leaf)})

(defn- poll-until-scores
  "Bounded poll: wait until the judge-scores projection holds at least
   `expected` entries for sheet-id, or poll-max-rounds elapse. Returns
   the entries found (possibly fewer than expected — reported as-is)."
  [ctx sheet-id expected]
  (loop [round 0]
    (let [entries (sheet-judge-entries ctx sheet-id)]
      (cond
        (>= (count entries) expected) entries
        (>= round poll-max-rounds) entries
        :else (do (Thread/sleep poll-interval-ms)
                  (recur (inc round)))))))

(defn -main [& _]
  (alter-var-root #'judges/*judge-provider* (constantly sys/provider-kw))
  (alter-var-root #'judges/*judge-model* (constantly sys/model))
  (let [sys (sys/start)
        ctx (:ctx sys)]
    (try
      (let [ld-enabled (ontology/get-living-description-enabled? ctx)
            _ (println "LD-ENABLED-READBACK:" ld-enabled)
            before (downstream-snapshot ctx)
            _ (println "BEFORE:" (pr-str before))
            sheet-id (orc/build-workflow! ctx judged-summarizer)
            _ (println "SHEET-ID:" sheet-id)
            declared (orc/get-judges ctx sheet-id)
            _ (println "DECLARED-JUDGES:" (pr-str (keys declared)))
            results
            (loop [i 1
                   acc []]
              (if (> i executions-planned)
                acc
                (let [result (orc/execute ctx sheet-id
                                          {:input smokes/synthetic-input}
                                          :timeout-ms 300000)
                      _ (println "EXEC" i "status:" (:status result)
                                 "outputs:" (pr-str (:outputs result)))
                      entries (poll-until-scores ctx sheet-id (* 2 i))
                      _ (println "AFTER-EXEC" i "judge-score-entries:"
                                 (count entries))]
                  (recur (inc i) (conj acc {:execution i
                                            :status (:status result)
                                            :entries-so-far (count entries)})))))
            entries (sheet-judge-entries ctx sheet-id)
            raw-score-events
            (into [] (es/read (:event-store ctx)
                              {:types #{:judge/score-emitted}
                               :tenant-id (:tenant-id ctx)}))
            sheet-events (filter #(= sheet-id (:sheet-id %)) raw-score-events)
            after (downstream-snapshot ctx)
            node (when-let [nid (:node-id (first entries))]
                   (orc/get-node ctx sheet-id nid))]
        (println "EXECUTIONS:" (pr-str results))
        (println "NODE:" (pr-str (select-keys node [:name :type])))
        (println "READMODEL-ENTRIES:")
        (doseq [e entries]
          (println " " (pr-str {:judge-name (:judge-name e)
                                :score (:score e)
                                :tick-id (:tick-id e)
                                :node-id (:node-id e)
                                :emitted-at (:emitted-at e)
                                :dimensions (mapv #(select-keys % [:name :score :weight])
                                                  (:dimensions e))
                                :feedback-chars (count (or (:feedback e) ""))})))
        (println "RAW-SCORE-EVENTS-FOR-SHEET:" (count sheet-events))
        (doseq [e sheet-events]
          (println " " (pr-str {:judge-name (:judge-name e)
                                :score (:score e)
                                :model-provenance (:model-provenance e)})))
        ;; Cross-check the public query fn on one tuple.
        (when-let [e (first entries)]
          (println "GET-JUDGE-SCORES-TUPLE-COUNT:"
                   (count (eval/get-judge-scores ctx sheet-id
                                                 (:node-id e) (:tick-id e)))))
        (println "AFTER:" (pr-str after))
        (println "SUMMARY:" (pr-str {:ld-enabled ld-enabled
                                      :sheet-id sheet-id
                                      :executions results
                                      :entries (count entries)
                                      :raw-events (count sheet-events)
                                      :before before
                                      :after after})))
      (finally (sys/stop sys))))
  (shutdown-agents)
  (System/exit 0))
