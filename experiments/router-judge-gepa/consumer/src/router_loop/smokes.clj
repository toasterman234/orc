(ns router-loop.smokes
  "Smoke suite for the full ORC self-improving loop install.
   Modes (argv[0]): a | b | c | d | full
     a    minimal workflow executes end-to-end on a synthetic input
     b    one tier-1 judge (:grounding) scores a synthetic node output
          (offline evaluate-single, GETTING-STARTED Phase 2 path)
     c    one bounded GEPA optimize! run on the synthetic workflow
     d    judge smoke against 3 val rows of data/labeled_v1.jsonl
          (decision records only — the dataset carries no item text;
           full detail goes to data/, aggregates to results/)
     full full-loop wiring proof: Living-Description opt-in command +
          read-back, baseline corpus seed, ColBERT bootstrap-reindex,
          reindex processor start, embed-text dimension check
   Every mode starts the real system (SQLite event store, LMDB cache,
   all registered processors) and stops it before exiting."
  (:require [router-loop.system :as sys]
            [ai.obney.orc.orc-service.interface :as orc]
            [ai.obney.orc.evaluation.interface :as eval]
            [ai.obney.orc.evaluation.core.judges :as judges]
            [ai.obney.orc.gepa.interface :as gepa]
            [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.time.interface :as time]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(def results-dir (str (System/getProperty "user.dir") "/../results"))
(def data-dir (str (System/getProperty "user.dir") "/../data"))

(defn- save! [name content]
  (let [f (io/file results-dir name)]
    (.mkdirs (.getParentFile f))
    (spit f content)
    (println "saved" (.getPath f))))

(defn- pin-judge-defaults!
  "Point the evaluation judges' dynamic defaults at cliproxy/luna.
   (Sheet judge configs can also carry :provider/:model per judge —
   judge_runtime binds from the config first, these vars are the fallback,
   and GEPA's judge metric reads them.)"
  []
  (alter-var-root #'judges/*judge-provider* (constantly sys/provider-kw))
  (alter-var-root #'judges/*judge-model* (constantly sys/model)))

(def summarizer
  (orc/workflow "summarizer"
    (orc/blackboard {:input :string :summary :string})
    (orc/sequence "main"
      (orc/llm "summarize"
        :model sys/model
        :instruction "Summarize the input text in 2 sentences."
        :reads [:input]
        :writes [:summary]))))

(def synthetic-input
  "The city council voted on Tuesday to approve the new library budget. The budget includes funding for extended evening hours, a children's reading program, and repairs to the roof. Opponents raised concerns about the cost, but the measure passed six to three. Construction is expected to begin in the spring and finish before the end of the year.")

(defn smoke-a [ctx]
  (let [sheet-id (orc/build-workflow! ctx summarizer)
        result (orc/execute ctx sheet-id {:input synthetic-input}
                            :timeout-ms 300000)
        out (pr-str result)]
    (save! "smoke-a-workflow.txt"
           (str "sheet-id: " sheet-id "\nresult: " out "\n"))
    (println "SMOKE-A status:" (:status result))
    (println "SMOKE-A outputs:" (:outputs result))
    result))

(defn smoke-b [_ctx]
  (let [trace {:inputs {:contract (str "The agreement is governed by California law. "
                                       "Disputes are subject to binding arbitration. "
                                       "Payment is due within 30 days of invoice.")}
               :outputs {:survey (str "The agreement is governed by California law and "
                                      "requires binding arbitration for disputes. "
                                      "Payment terms are net 60.")}
               :instruction "Survey the structure and key provisions of the contract."}
        result (judges/with-judge-config {:provider sys/provider-kw :model sys/model}
                 (eval/evaluate-single :grounding trace))
        out (pr-str result)]
    (save! "smoke-b-judge.txt" (str "judge: :grounding (tier-1)\nresult: " out "\n"))
    (println "SMOKE-B result:" out)
    result))

(def gepa-trainset
  ;; NOTE: :gepa/start-optimization validates datasets as
  ;; [:vector [:map-of :string :any]] — string keys, not keywords.
  [{"input" "A local bakery announced it will open a second location downtown next month. The owner said the new shop will hire twelve people and offer baking classes on weekends. The first location opened in 2019 and is known for its sourdough bread."}
   {"input" "The university's robotics team won the regional championship on Saturday. Their robot completed the course in 48 seconds, beating the previous record by three seconds. The team will now advance to the national finals in June."}
   {"input" "City engineers reported that the bridge inspection found no structural damage. The bridge will reopen to traffic on Friday after two weeks of lane closures. A full report will be published next month."}])

(def gepa-valset
  [{"input" "The farmers market will move to the parking lot on Fifth Street starting in April. Organizers said the new location offers more space for vendors and better parking for visitors. The market runs every Saturday morning."}
   {"input" "A new study found that regular walking improves memory in older adults. Participants who walked thirty minutes a day scored higher on recall tests after six months. The researchers recommend further study on intensity and duration."}])

(defn smoke-c [ctx]
  (let [sheet-id (orc/build-workflow! ctx summarizer)
        metric-fn (gepa/make-judge-metric {:grounding 0.5 :completeness 0.5})
        result (gepa/optimize! ctx
                 {:sheet-id sheet-id
                  :trainset gepa-trainset
                  :valset gepa-valset
                  :metric-fn metric-fn
                  :config {:max-metric-calls 24
                           :reflection-lm sys/model}
                  :block? true
                  :timeout-ms 900000})]
    (save! "smoke-c-gepa.txt" (str "result: " (pr-str result) "\n"))
    (println "SMOKE-C status:" (:status result))
    (println "SMOKE-C best-score:" (:best-score result))
    (println "SMOKE-C best-candidate:" (pr-str (:best-candidate result)))
    result))

(defn smoke-d [_ctx]
  (let [rows (->> (line-seq (io/reader (io/file data-dir "labeled_v1.jsonl")))
                  (map #(json/parse-string % true))
                  (filter #(= "val" (:split %)))
                  (take 3)
                  vec)
        run-one (fn [idx row]
                  (let [ro (:router_output row)
                        gold (:run2_gold row)
                        trace {:inputs {:classification
                                        (str "kind=" (:kind gold) ", effort=" (:effort gold)
                                             ", planning=" (:planning_required gold)
                                             ", impact=" (:impact gold)
                                             ", actionable=" (:is_actionable gold)
                                             ", blockers=" (:blockers gold))}
                               :outputs {:route-decision
                                         (str "route=" (:route_key ro) ", stage=" (:stage ro)
                                              " (" (:stage_reason ro) ")")}
                               :instruction (str "Route this item to the single workflow "
                                                 "that should own it next, based only on its "
                                                 "classification fields.")}]
                    (try
                      (let [res (judges/with-judge-config {:provider sys/provider-kw :model sys/model}
                                  (eval/evaluate-single :grounding trace))]
                        {:idx idx :ok true :result res})
                      (catch Exception e
                        {:idx idx :ok false :error (ex-message e)}))))
        results (mapv run-one (range) rows)
        scores (keep (fn [r] (when (:ok r)
                               (or (:score (:grounding-result (:result r)))
                                   (:score (:result r)))))
                     results)]
    (spit (io/file data-dir "judge_smoke_d_detail.edn") (pr-str results))
    (save! "smoke-d-router-judge.txt"
           (str "items judged: " (count results) "\n"
                "ok: " (count (filter :ok results)) "\n"
                "scores (grounding, decision records): " (pr-str (vec scores)) "\n"
                "note: full per-item detail in data/judge_smoke_d_detail.edn (gitignored). "
                "The labeled dataset carries classification fields + route/stage only, "
                "no item text — judges scored the decision record against the gold "
                "classification, per the router mapping.\n"))
    (println "SMOKE-D ok:" (count (filter :ok results)) "/" (count results)
             "scores:" (pr-str (vec scores)))
    results))

(defn smoke-full [sys]
  (let [ctx (:ctx sys)
        _ (cp/process-command
           (assoc ctx :command
                  {:command/name :ontology/set-living-description-enabled
                   :command/id (random-uuid)
                   :command/timestamp (time/now)
                   :enabled? true}))
        _ (Thread/sleep 500)
        enabled? (ontology/get-living-description-enabled? ctx)
        _ (ontology/seed-baseline-corpus! ctx)
        _ (ontology/bootstrap-reindex! ctx)
        sys2 (sys/start-reindex-processor! sys)
        vec (ontology/embed-text "A contract clause about payment terms")
        summary {:living-description-enabled enabled?
                 :embed-dim (count vec)
                 :processors-running (count (:processors sys2))}]
    (save! "smoke-full-loop.txt" (str (pr-str summary) "\n"))
    (println "SMOKE-FULL:" (pr-str summary))
    [sys2 summary]))

(defn -main [& [mode]]
  (pin-judge-defaults!)
  (let [sys (sys/start)
        ctx (:ctx sys)]
    (try
      (case mode
        "a" (smoke-a ctx)
        "b" (smoke-b ctx)
        "c" (smoke-c ctx)
        "d" (smoke-d ctx)
        "full" (smoke-full sys)
        (println "usage: smokes <a|b|c|d|full>"))
      (finally (sys/stop sys))))
  (shutdown-agents)
  (System/exit 0))
