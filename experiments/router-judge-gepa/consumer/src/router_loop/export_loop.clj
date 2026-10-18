(ns router-loop.export-loop
  "READ-ONLY live-state export for ORC layers 6-8 (Loop tab data source).

   Starts the consumer system exactly like the smokes (SQLite event store,
   LMDB cache, registered processors), then ONLY queries / projects read
   models and reads events back. It issues NO commands: no
   set-living-description-enabled, no seed-baseline-corpus!, no
   bootstrap-reindex!, no build-workflow!, no execute, no GEPA. The one
   system-level action is sys/start-reindex-processor! (starting a
   processor, as smoke-full did after seeding) so the running-processor
   count reflects the full-loop end state; any events that startup itself
   appends are measured by the caller's before/after SQLite census and
   recorded in the summary — this program also takes its own in-JVM census
   via es/read at query time.

   No LLM calls: nothing in this namespace invokes an LLM node, judge,
   proposer, classifier or consolidator command; queries are projections
   over already-persisted LMDB read-model state."
  (:require [router-loop.system :as sys]
            [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [ai.obney.grain.todo-processor-v2.interface :as tp]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.event-store-v3.interface :as es]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string])
  (:import [java.time Instant]))

(def results-dir (str (System/getProperty "user.dir") "/../results"))

(defn- jsonable
  "Total coercion of read-model values to JSON-safe data."
  [x]
  (cond
    (nil? x) nil
    (string? x) x
    (keyword? x) (let [n (namespace x)] (if n (str n "/" (name x)) (name x)))
    (boolean? x) x
    (integer? x) x
    (float? x) (double x)
    (decimal? x) (double x)
    (ratio? x) (double x)
    (uuid? x) (str x)
    (map? x) (into {} (map (fn [[k v]] [(jsonable k) (jsonable v)])) x)
    (set? x) (mapv jsonable (sort-by str x))
    (sequential? x) (mapv jsonable x)
    :else (str x)))

(defn- attempt
  "Run a query fn, returning {:ok value} or {:error message} — one failed
   optional query must not sink the whole export."
  [f]
  (try {:ok (f)}
       (catch Throwable t {:error (str (.getSimpleName (class t)) ": " (ex-message t))})))

(defn- registry-keys
  "Keys of a Grain registry (atom or map), as strings."
  [reg]
  (let [m (if (instance? clojure.lang.IDeref reg) @reg reg)]
    (cond
      (map? m) (mapv str (keys m))
      (coll? m) (mapv str m)
      :else [])))

(defn- project-state [ctx read-model]
  (attempt #(rmp/project ctx read-model)))

(defn export! []
  (let [sys1 (sys/start)
        ctx (:ctx sys1)
        started-at-boot (count (:processors sys1))
        registered (count @tp/processor-registry*)
        _ (Thread/sleep 2000)
        ;; Mirror smoke-full's end state: start the reindex processor that
        ;; boot skips. This starts a processor; it issues no command.
        sys2 (sys/start-reindex-processor! sys1)
        ctx (:ctx sys2)
        _ (Thread/sleep 2000)
        running (count (:processors sys2))

        enabled? (attempt #(ontology/get-living-description-enabled? ctx))
        ontologies (attempt #(ontology/list-ontologies ctx))
        concepts (attempt #(ontology/get-concepts ctx))
        concept-stats (attempt #(ontology/concept-statistics ctx))
        concepts-state (project-state ctx :ontology/concepts)
        ontologies-state (project-state ctx :ontology/ontologies)
        descriptions-state (project-state ctx :ontology/descriptions)
        tree-profiles-state (project-state ctx :ontology/tree-profiles)
        node-experiences-state (project-state ctx :ontology/node-experiences)
        judge-scores-state (project-state ctx :evaluation/judge-scores)
        recent-consol (into {}
                             (map (fn [tt]
                                    [(str tt) (attempt #(ontology/get-recent-consolidation-count ctx tt))]))
                             [:node-type :node-instance :tree-fingerprint :tree-class])
        unanswered-consol (attempt #(ontology/get-unanswered-consolidation-requests ctx {}))
        node-learnings (attempt #(ontology/get-all-node-learnings ctx))
        node-learning-stats (attempt #(ontology/node-learning-statistics ctx))

        events (attempt #(into [] (es/read (:event-store ctx) {:tenant-id sys/tenant-id})))
        event-census (when (contains? events :ok)
                       (frequencies (map :event/type (:ok events))))
        mint-events (when (contains? events :ok)
                      (filter #(= :ontology/behavioral-subtree-minted (:event/type %)) (:ok events)))
        evo-events (when (contains? events :ok)
                     (filter #(contains? #{:evolutionary/concepts-extracted
                                           :evolutionary/relationships-extracted}
                                         (:event/type %)) (:ok events)))
        ontology-created-events (when (contains? events :ok)
                                  (filter #(= :ontology/ontology-created (:event/type %)) (:ok events)))

        cmd-keys (registry-keys (cp/global-command-registry))
        query-keys (registry-keys (qp/global-query-registry))
        mcp-cmds (filterv #(clojure.string/includes? % "mcp") cmd-keys)
        mcp-queries (filterv #(clojure.string/includes? % "mcp") query-keys)

        descriptions (when (contains? descriptions-state :ok)
                       (for [[granularity targets] (:ok descriptions-state)
                             [target-id entry] targets]
                         {:granularity (jsonable granularity)
                          :target-id (jsonable target-id)
                          :name (jsonable (or (:name (:current entry))
                                              (:label (:current entry))
                                              (:title (:current entry))))
                          :history-versions (count (:history entry))
                          :claims (count (:claims entry))
                          :claim-set-version (:claim-set-version entry 0)}))
        concepts-list (when (contains? concepts :ok)
                        (mapv (fn [c]
                                {:uri (jsonable (:uri c))
                                 :id (jsonable (:id c))
                                 :label (jsonable (:label c))
                                 :scope (jsonable (:scope c))
                                 :ontology-id (jsonable (:ontology-id c))
                                 :provenance-kind (jsonable (:kind (:provenance c)))
                                 :lifecycle-status (jsonable (or (:status c) (:lifecycle c)))})
                              (:ok concepts)))]

    (sys/stop sys2)

    {:generated-at (str (Instant/now))
     :tenant-id (str sys/tenant-id)
     :method {:route "router-loop.export-loop: sys/start (same wiring as the smokes), queries/projections only, sys/start-reindex-processor! to mirror the full-loop end state, sys/stop"
              :commands-issued []
              :llm-calls-by-this-program 0}
     :processors {:registered-in-todo-processor-registry registered
                  :started-at-boot started-at-boot
                  :skipped-at-boot [":ontology/on-description-updated-maybe-reindex"]
                  :running-after-reindex-processor-start running}
     :event-census-at-query-time (when event-census
                                   {:total-events (count (:ok events))
                                    :by-type (into (sorted-map)
                                                   (map (fn [[k v]] [(str k) v]))
                                                   event-census)})
     :layer6 {:ontologies (jsonable (:ok ontologies))
              :ontology-created-events {:count (count ontology-created-events)
                                        :detail (mapv (fn [e]
                                                        {:ontology-id (jsonable (:ontology-id e))
                                                         :name (jsonable (:name e))
                                                         :scope (jsonable (:scope e))})
                                                      ontology-created-events)
                                        :note "Detail lists every :ontology/ontology-created event in the stream (id + name + scope as emitted); the ontologies read model keys by :ontology-id, so repeat creations of the same stable id collapse to one live entry."}
              :ontologies-read-model-entries (when (contains? ontologies-state :ok)
                                               (count (:ok ontologies-state)))
              :concepts {:count (when (contains? concepts :ok) (count (:ok concepts)))
                         :read-model-entries (when (contains? concepts-state :ok)
                                               (count (:ok concepts-state)))
                         :statistics (jsonable (:ok concept-stats))
                         :list concepts-list
                         :lifecycle-status-note "The concepts read model carries no :status/:lifecycle field per entry (checked :status and :lifecycle keys); entries carry :scope and :provenance instead."
                         :concept-created-events (get event-census :ontology/concept-created 0)
                         :relationship-created-events (get event-census :ontology/relationship-created 0)}
              :evolutionary-builder {:exercised (boolean (seq evo-events))
                                     :concepts-extracted-events (count (filter #(= :evolutionary/concepts-extracted (:event/type %)) evo-events))
                                     :relationships-extracted-events (count (filter #(= :evolutionary/relationships-extracted (:event/type %)) evo-events))
                                     :evidence "es/read over the full tenant stream in this run + the SQLite type census: neither :evolutionary/* event type exists in the store; consumer/src contains no build-from-sources / evolve call (grep). Interface fns live in components/ontology/src/ai/obney/orc/ontology/interface/evolutionary.clj (loaded via the ontology component)."}}
     :layer7 {:living-description-enabled {:value (:ok enabled?)
                                            :evidence "ontology/get-living-description-enabled? (query over :ontology/living-description-enabled read model) on the freshly started system; no set command issued by this program"
                                            :enabled-set-events (get event-census :ontology/living-description-enabled-set 0)}
              :living-descriptions {:total (count descriptions)
                                    :by-granularity (when descriptions
                                                      (frequencies (map :granularity descriptions)))
                                    :list (vec descriptions)
                                    :evidence "Full projection of the :ontology/descriptions read model (rmp/project): {granularity {target-id {:current :history :claims}}}; name = :name/:label/:title of the current body when present, else null (target-id is the identifier)."}
              :claims {:claim-deltas-recorded-events (get event-census :ontology/claim-deltas-recorded 0)
                       :targets-with-claims (when descriptions (count (filter #(pos? (:claims %)) descriptions)))}
              :judge-scores {:read-model-entries (when (contains? judge-scores-state :ok)
                                                   (count (:ok judge-scores-state)))
                             :score-entries-total (when (contains? judge-scores-state :ok)
                                                    (reduce + 0 (map count (vals (:ok judge-scores-state)))))
                             :score-emitted-events (get event-census :judge/score-emitted 0)
                             :composite-score-events (get event-census :judge/composite-score-computed 0)
                             :note "Read-model-entries counts [sheet tick node] tuples; score-entries-total counts individual judge scores. Judge calls in the install smokes and the blocker-triage calibration used the offline evaluate-single path (no events). The scores present here came from Part B (results/judge-persistence.md): tier-1 judges ATTACHED to the summarizer-judged sheet's leaf, fired by the :evaluation/on-node-execution-completed processor, written by the :evaluation/record-judge-score commands — the only path that emits :judge/score-emitted."}
              :auto-classified-executions {:tree-profiles-count (when (contains? tree-profiles-state :ok)
                                                                   (count (:ok tree-profiles-state)))
                                           :tree-strength-recorded-events (get event-census :ontology/tree-strength-recorded 0)
                                           :tree-weakness-recorded-events (get event-census :ontology/tree-weakness-recorded 0)
                                           :tree-problem-mapping-events (+ (get event-census :ontology/tree-problem-mapping-created 0)
                                                                           (get event-census :ontology/tree-problem-mapping-updated 0))
                                           :evidence "Tree profiles are the read model that execution classification lands in; zero profile events of any kind exist in the store."}
              :node-experiences {:read-model-entries (when (contains? node-experiences-state :ok)
                                                       (count (:ok node-experiences-state)))
                                 :node-pattern-learned-events (get event-census :ontology/node-pattern-learned 0)
                                 :learnings (jsonable (:ok node-learnings))
                                 :statistics (jsonable (:ok node-learning-stats))}
              :consolidations {:requested-events (get event-census :ontology/consolidation-requested 0)
                               :recent-count-by-target-type (jsonable recent-consol)
                               :unanswered-requests (jsonable unanswered-consol)
                               :claim-deltas-recorded-events (get event-census :ontology/claim-deltas-recorded 0)
                               :note "Consolidation requests fired from the threshold trigger during the install smokes' repeated leaf executions (results/learning-diagnosis.md). Root cause of their original orphaning (proven): the consuming processor (:ontology/consolidate-on-request, ontology/core/consolidator.clj) was required by no production or consumer namespace, so it was never registered. Fixed consumer-side 2026-10-17 (router-loop.system requires the namespace + pins :ontology-consolidator-model): registry 34 -> 35. Ledger now fully answered: 8 requested — 2 completed (node-type leaf description v1, consolidation-wiring.md; node-instance [smoke-a summarize] description v1, consolidation-node-instance.md), 6 swept to :ontology/description-consolidation-failed (:caller-interrupted, attempts 0), 0 unanswered. Claim deltas remain 0 by design (node-type/node-instance targets take the body path; claim deltas are :tree-class-only, CC-5)."}
              :behavior-mints {:behavioral-subtree-minted-events (count mint-events)
                               :provenance-frequencies (when mint-events
                                                         (frequencies (map #(jsonable (:provenance %)) mint-events)))
                               :note "All mints in the store come from seed-baseline-corpus! (E3 durable coding subbehaviors, command carries :provenance :human-authored) — bootstrap content, not runtime volitional minting or promotion of an execution pattern."}}
     :layer8 {:interface-loaded (boolean (find-ns 'ai.obney.orc.mcp-sheet-builder.interface))
              :interface-namespace "ai.obney.orc.mcp-sheet-builder.interface"
              :registered-commands-containing-mcp mcp-cmds
              :registered-queries-containing-mcp mcp-queries
              :registry-note "The MCP Sheet Builder registers NO Grain commands or queries (grep of components/mcp-sheet-builder/src for defcommand/defquery is empty); it is a plain function API (connect, list-tools, generate-sheet, ...) required by router-loop.system for side-effect registration alongside the other components."
              :mcp-servers-configured 0
              :sheets-generated-from-mcp-schemas 0
              :evidence "No :mcp* event types exist in the store census; no consumer code calls any mcp-sheet-builder function (grep of consumer/src shows only the :require in router-loop.system); no MCP connection config exists in the consumer."}}))

(defn -main [& _args]
  (let [state (export!)
        out (io/file results-dir "loop_state.json")]
    (.mkdirs (.getParentFile out))
    (spit out (json/generate-string state {:pretty true}))
    (println "LOOP-EXPORT processors:" (pr-str (:processors state)))
    (println "LOOP-EXPORT enabled:" (pr-str (get-in state [:layer7 :living-description-enabled :value])))
    (println "LOOP-EXPORT concepts:" (pr-str (get-in state [:layer6 :concepts :count])))
    (println "LOOP-EXPORT descriptions:" (pr-str (get-in state [:layer7 :living-descriptions :total])))
    (println "LOOP-EXPORT census-total:" (pr-str (get-in state [:event-census-at-query-time :total-events])))
    (println "saved" (.getPath out)))
  (shutdown-agents)
  (System/exit 0))
