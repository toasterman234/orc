(ns router-loop.blocker-triage
  "Phase 2 of the FIT follow-through (Ben's 2026-10-17 ORC-native
   decision, PLAN.md §8): calibration of ONE ORC-native judge config
   against the master-repo-0.1 blocker-triage eval suite.

   Judge config (native idiom): the evaluation component's tier-1
   :reasoning judge — criteria x stance x discrete 1-5 scale, evidence
   before score — with the per-call criteria OVERRIDE seam (RR-31)
   carrying criteria authored from the suite (data-only file:
   data/eval-suites/blocker-triage/judge_criteria.txt — principle from
   target_behavior, must_not_generalize_to folded in as explicit
   failure conditions, the six suite criteria, the hard-fail list).
   The stance is NOT overridden: the tier-1 seam replaces criteria
   only, so the stance remains the reasoning dimension's built-in
   adversarial stance. Provider/model pinned to cliproxy + gpt-5.6-luna
   exactly as the install smokes pinned them.

   Exam: the LOCKED valset (data/eval-suites/blocker-triage/
   gepa_valset.json; lock + hashes in valset.lock.json, built by
   src/build_blocker_triage.py). 12 items: the suite's captured
   baseline response (harness ground truth 1/10 -> fail-side), its
   post-guidance candidate response (10/10 -> pass-side), and for each
   of the 5 counterexample templates an authored correct-decision and
   wrong-decision statement.

   Pre-registered scoring (written here BEFORE the run):
     judge side: level >= 4 -> pass, level <= 2 -> fail, level 3 ->
     partial (agreement 0.5). Agreement rate = (matches + 0.5 *
     partials) / 12.
     PASSED  — rate >= 0.80 AND both base items are full matches.
     FAILED  — rate < 0.50 OR either base item is a full mismatch.
     PARTIAL — otherwise.
   Hard cap: 60 LLM calls for the phase (1 per item + at most 1 retry
   per item on a gate throw). Full judge evidence goes to data/; the
   committed results file carries aggregates + per-item verdicts only."
  (:require [router-loop.system :as sys]
            [ai.obney.orc.evaluation.core.judges :as judges]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def suite-dir (str (System/getProperty "user.dir") "/../data/eval-suites/blocker-triage"))
(def results-file (str (System/getProperty "user.dir") "/../results/blocker-triage-calibration.md"))
(def call-cap 60)
(def calls (atom 0))

(defn- pin-judge-defaults! []
  (alter-var-root #'judges/*judge-provider* (constantly sys/provider-kw))
  (alter-var-root #'judges/*judge-model* (constantly sys/model)))

(defn- task-of
  "The producer instruction: the rendered input's first line is
   'TASK: <task_context>' (the suite's own task text)."
  [input]
  (let [first-line (first (str/split-lines input))]
    (if (str/starts-with? first-line "TASK: ")
      (subs first-line 6)
      "Triage the deployment preflight per the scenario.")))

(defn- judge-one
  "One valset item through the reasoning judge. Retries once if the
   no-run-through gate throws; every attempt counts against the cap."
  [criteria item]
  (let [trace {:inputs {:scenario (:input item)}
               :outputs {:response (:response item)}
               :instruction (task-of (:input item))}
        attempt (fn []
                  (swap! calls inc)
                  (when (> @calls call-cap)
                    (throw (ex-info (str "LLM call cap exceeded (" call-cap ")") {})))
                  (judges/with-judge-config {:provider sys/provider-kw :model sys/model}
                    (judges/reasoning-judge {:inputs {:trace-data trace
                                                      :criteria criteria}})))]
    (try
      {:ok true :attempts 1 :result (attempt)}
      (catch Throwable t1
        (try
          {:ok true :attempts 2 :result (attempt)}
          (catch Throwable t2
            {:ok false :attempts 2 :error (str (.getSimpleName (class t2)) ": "
                                               (ex-message t2))
             :first-error (str (.getSimpleName (class t1)) ": " (ex-message t1))}))))))

(defn- side-of [level]
  (cond (>= level 4) "pass"
        (<= level 2) "fail"
        :else "partial"))

(defn- verdict [scored]
  (let [n (count scored)
        rate (/ (reduce + (map :agreement scored)) (double n))
        base (into {} (map (juxt :id identity)) (filter #(str/starts-with? (:id %) "base-") scored))
        base-ok (every? #(= 1.0 (:agreement %)) (vals base))]
    (cond
      (or (< rate 0.5)
          (some #(= 0.0 (:agreement %)) (vals base))) "FAILED"
      (and (>= rate 0.8) base-ok) "PASSED"
      :else "PARTIAL")))

(defn- usage-total [usage]
  (when (map? usage)
    (or (:total_tokens usage) (:total-tokens usage)
        (let [i (or (:input_tokens usage) (:prompt_tokens usage) (:input-tokens usage))
              o (or (:output_tokens usage) (:completion_tokens usage) (:output-tokens usage))]
          (when (and i o) (+ i o))))))

(defn run! []
  (pin-judge-defaults!)
  (let [criteria (slurp (io/file suite-dir "judge_criteria.txt"))
        valset (json/parse-string (slurp (io/file suite-dir "gepa_valset.json")) true)
        lock (json/parse-string (slurp (io/file suite-dir "valset.lock.json")) true)
        scored (mapv (fn [item]
                       (let [r (judge-one criteria item)]
                         (if (:ok r)
                           (let [res (:reasoning-result (:result r))
                                 level (:level res)
                                 side (side-of level)
                                 expected (:expected_side item)
                                 agreement (cond (= side "partial") 0.5
                                                 (= side expected) 1.0
                                                 :else 0.0)]
                             {:id (:id item)
                              :expected-side expected
                              :judge-side side
                              :level level
                              :score (:score res)
                              :agreement agreement
                              :attempts (:attempts r)
                              :usage (:usage (:model-provenance res))
                              :reasoning (:reasoning res)
                              :reasoning-strengths (:reasoning-strengths res)
                              :reasoning-weaknesses (:reasoning-weaknesses res)
                              :feedback (:feedback res)})
                           {:id (:id item)
                            :expected-side (:expected_side item)
                            :judge-side "error"
                            :agreement 0.0
                            :attempts (:attempts r)
                            :error (:error r)
                            :first-error (:first-error r)})))
                     valset)
        matches (count (filter #(= 1.0 (:agreement %)) scored))
        partials (count (filter #(= 0.5 (:agreement %)) scored))
        mismatches (count (filter #(= 0.0 (:agreement %)) scored))
        rate (/ (+ matches (* 0.5 partials)) (double (count scored)))
        total-tokens (reduce + (keep #(usage-total (:usage %)) scored))
        v (verdict scored)]
    (spit (io/file suite-dir "calibration_detail.json")
          (json/generate-string {:judge {:dimension "reasoning"
                                         :provider (str sys/provider-kw)
                                         :model sys/model
                                         :criteria-file "data/eval-suites/blocker-triage/judge_criteria.txt"
                                         :valset-sha256 (:valset_sha256 lock)
                                         :cap call-cap
                                         :calls @calls}
                                 :verdict v
                                 :agreement-rate rate
                                 :items scored}
                                {:pretty true}))
    (spit results-file
          (str "# Blocker-triage judge calibration — results (FIT #1, ORC-native)\n\n"
               "- **Date:** 2026-10-17 · **Verdict: " v "** · agreement rate "
               (format "%.3f" rate) " (" matches " full matches, " partials
               " partials, " mismatches " mismatches / " (count scored) " items)\n"
               "- **Judge:** ORC tier-1 `:reasoning` (criteria × adversarial stance × "
               "discrete 1–5 scale, evidence before score), criteria overridden per call "
               "with text authored from the suite (principle + must_not_generalize_to as "
               "failure conditions + the six suite criteria + hard-fail list). Model "
               sys/model " via cliproxy (Codex subscription). Stance is the dimension's "
               "built-in adversarial stance — the tier-1 seam overrides criteria, not stance.\n"
               "- **Exam:** locked valset, sha256 `" (:valset_sha256 lock) "` "
               "(data-only; lock record in data). 12 items: the suite's harness-scored "
               "pair (baseline captured response, ground truth 1/10 → fail-side; candidate "
               "response, 10/10 → pass-side) + 5 counterexample templates × authored "
               "correct/wrong decision statements.\n"
               "- **Scoring rule (pre-registered in `consumer/src/router_loop/blocker_triage.clj`):** "
               "level ≥ 4 = pass-side, ≤ 2 = fail-side, 3 = partial (½ credit). PASSED = "
               "rate ≥ 0.80 with both base items full matches; FAILED = rate < 0.50 or a "
               "base item fully mismatched; else PARTIAL.\n"
               "- **LLM spend:** " @calls " judge calls (cap " call-cap "), ~"
               total-tokens " total tokens reported by the provider across items "
               "(per-item usage in the data-only detail file).\n\n"
               "| Item | Expected | Judge level | Judge score | Judge side | Agreement |\n"
               "|---|---|---|---|---|---|\n"
               (str/join "\n"
                         (map (fn [s]
                                (str "| " (:id s) " | " (:expected-side s) " | "
                                     (or (:level s) "—") " | "
                                     (if (:score s) (format "%.2f" (double (:score s))) "—")
                                     " | " (:judge-side s) " | " (:agreement s) " |"))
                              scored))
               "\n\nFull judge reasoning/evidence per item: "
               "`data/eval-suites/blocker-triage/calibration_detail.json` (gitignored — "
               "it quotes scenario material). Method, field mapping, and limitations: "
               "`blocker-triage-mapping-memo.md`. Suite ground truth: baseline 1/10, "
               "candidate 10/10, pass threshold 8/10 (the suite's own deterministic "
               "harness, replicated token-for-token in `src/build_blocker_triage.py`).\n"))
    (println "CALIBRATION verdict:" v "rate:" rate "calls:" @calls "tokens:" total-tokens)
    (doseq [s scored]
      (println " " (:id s) "expected" (:expected-side s) "level" (:level s)
               "side" (:judge-side s) "agreement" (:agreement s)))
    {:verdict v :rate rate :calls @calls :tokens total-tokens}))

(defn -main [& _args]
  ;; Same wiring as the install smokes: start the real system (which
  ;; installs the stdio bridge + registers the provider), then judge via
  ;; the offline evaluate-single path — no commands are issued and the
  ;; path emits no events (established in the install pass, PREFLIGHT
  ;; §7.7 smoke (d)).
  (let [sys (sys/start)]
    (try
      (run!)
      (finally (sys/stop sys))))
  (shutdown-agents)
  (System/exit 0))
