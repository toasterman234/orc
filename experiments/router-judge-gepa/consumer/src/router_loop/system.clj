(ns router-loop.system
  "Consumer wiring for the FULL ORC self-improving loop, pointed at Ben's
   Codex subscription via CLIProxyAPI on the Mac mini (Ben's order,
   2026-10-17 — NOT OpenRouter, no new key).

   Provider path: JVM -> http://127.0.0.1:18317/v1 (SSH local-forward,
   scripts/cliproxy-tunnel.sh) -> Mac 127.0.0.1:8317 CLIProxyAPI -> Codex
   subscription. Model: gpt-5.6-luna for nodes, judges and the GEPA
   proposer alike (most efficient tier that completed the smoke;
   gpt-5.6-terra is the documented escalation).

   The ctx follows docs/GETTING-STARTED.md (Phase 1 wiring) with the bench
   runner's processor pattern: every registered todo-processor is started
   EXCEPT :ontology/on-description-updated-maybe-reindex, which is started
   only after corpus seeding so 68+ seed events don't trigger a storm of
   parallel ColBERT rebuilds (see development/bench/runner.clj)."
  (:require [router-loop.bridge :as bridge]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.event-store-v3.interface.schemas]
            [ai.obney.grain.event-store-sqlite-v3.interface]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.pubsub.interface :as ps]
            [ai.obney.grain.todo-processor-v2.interface :as tp]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.query-processor.interface :as qp]
            [litellm.router :as litellm-router]
            ;; Load the full loop's components for side-effect registration
            ;; of their commands, queries, read models and processors.
            [ai.obney.orc.orc-service.interface :as orc]
            [ai.obney.orc.evaluation.interface]
            [ai.obney.orc.evaluation.interface.schemas]
            [ai.obney.orc.gepa.interface]
            [ai.obney.orc.gepa.interface.schemas]
            [ai.obney.orc.ontology.interface]
            [ai.obney.orc.ontology.interface.schemas]
            [ai.obney.orc.ontology.core.commands]
            [ai.obney.orc.ontology.core.read-models]
            ;; Ben's directive 2026-10-17 ("wire consolidation next"): the
            ;; consolidator processor (:ontology/consolidate-on-request) is
            ;; a defprocessor in this namespace, and Grain registers
            ;; defprocessors as a namespace-LOAD side effect. Neither ORC's
            ;; ontology interface nor this consumer loaded it, so the 6
            ;; :ontology/consolidation-requested events in the store were
            ;; orphaned (root cause: results/learning-diagnosis.md).
            ;; Upstream's own consumers load it explicitly the same way
            ;; (development/bench/cc17_costs.clj,
            ;; development/src/c_loop_1_live_verify.clj). Bare require =
            ;; registration only; the processor is started by `start`
            ;; below like every other registered processor.
            [ai.obney.orc.ontology.core.consolidator]
            [ai.obney.orc.colbert.interface]
            [ai.obney.orc.colbert.interface.schemas]
            [ai.obney.orc.mcp-sheet-builder.interface]))

(def provider-kw
  "Registered under litellm's :openai keyword on purpose: litellm's
   supports-function-calling? dispatches on this keyword, and :openai is
   what enables function-calling mode (typed tool-call outputs). A custom
   keyword falls to the :default method (false) and forces DSPy marker
   mode, whose scalar sections the Codex models render JSON-quoted
   (\":level \\\"2\\\"\") — the evaluation banding gate then rightly trips.
   The endpoint is still cliproxy via the tunnel; only the registry
   keyword is :openai."
  :openai)
(def model "gpt-5.6-luna")
(def escalation-model "gpt-5.6-terra")
(def tenant-id #uuid "11111111-1111-1111-1111-111111111111")

(def runtime-dir
  (str (System/getProperty "user.dir") "/runtime"))

(defn register-provider!
  "Register the cliproxy endpoint as an OpenAI-compatible litellm provider.
   Key comes from the environment (data/cliproxy.env, gitignored, 0600) —
   it is never printed or persisted by this code."
  []
  (let [api-key (System/getenv "CLIPROXY_API_KEY")
        api-base (or (System/getenv "CLIPROXY_BASE_URL")
                     "http://127.0.0.1:18317/v1")]
    (when-not (and api-key (not (.isBlank api-key)))
      (throw (ex-info "CLIPROXY_API_KEY is not set — source data/cliproxy.env (run-clj.sh does this)" {})))
    (litellm-router/register! provider-kw
                              {:provider :openai
                               :model model
                               :config {:api-base api-base
                                        :api-key api-key}})
    (println "provider registered:" provider-kw "model:" model "base:" api-base)))

(def ^:private skip-at-start #{:ontology/on-description-updated-maybe-reindex})

(defn start
  "Start the full-loop system. Returns a system map; pass to stop."
  []
  ;; The sandbox denies JVM direct TCP (see scripts/http_bridge.py);
  ;; install the stdio socket bridge before any LLM call can happen.
  (bridge/install!)
  (register-provider!)
  (let [event-pubsub (ps/start {:type :core-async :topic-fn :event/type})
        event-store (es/start {:conn {:type :sqlite
                                      :database-file (str runtime-dir "/events.db")}
                               :event-pubsub event-pubsub
                               :logger nil})
        cache (kv/start (lmdb/->KV-Store-LMDB
                         {:storage-dir (str runtime-dir "/lmdb")
                          :db-name "projections"
                          :map-size (* 2 1024 1024 1024)}))
        ctx {:tenant-id tenant-id
             :event-store event-store
             :event-pubsub event-pubsub
             :cache cache
             :command-registry (cp/global-command-registry)
             :query-registry (qp/global-query-registry)
             :llm-provider provider-kw
             :ontology-reranker-model model
             ;; Consolidator reflection model pin (2026-10-17 wiring):
             ;; consolidator.clj resolves its reflection :llm node's model
             ;; as (or (:ontology-consolidator-model ctx) (:model ctx));
             ;; this ctx previously carried NEITHER key, so the reflection
             ;; would have been built with :model nil. Pin the specific
             ;; key to the same luna model everything else uses.
             :ontology-consolidator-model model}
        processors (reduce-kv
                    (fn [acc proc-name {:keys [handler-fn topics]}]
                      (if (contains? skip-at-start proc-name)
                        acc
                        (assoc acc proc-name
                               (tp/start {:event-pubsub event-pubsub
                                          :topics topics
                                          :handler-fn handler-fn
                                          :context ctx}))))
                    {}
                    @tp/processor-registry*)]
    (println "system started: processors=" (count processors)
             "skipped-at-start=" (vec skip-at-start))
    {:ctx ctx :event-store event-store :event-pubsub event-pubsub
     :cache cache :processors processors}))

(defn start-reindex-processor!
  "Start the ColBERT reindex processor that was skipped at boot. Call AFTER
   corpus seeding + bootstrap-reindex! so seed events don't each trigger a
   rebuild."
  [{:keys [ctx event-pubsub] :as sys}]
  (let [proc-name :ontology/on-description-updated-maybe-reindex
        {:keys [handler-fn topics]} (get @tp/processor-registry* proc-name)]
    (when-not handler-fn
      (throw (ex-info "reindex processor not found in registry" {:name proc-name})))
    (assoc sys :processors
           (assoc (:processors sys) proc-name
                  (tp/start {:event-pubsub event-pubsub
                             :topics topics
                             :handler-fn handler-fn
                             :context ctx})))))

(defn stop [{:keys [processors cache event-store event-pubsub]}]
  (doseq [[_ p] processors] (tp/stop p))
  (when event-pubsub (ps/stop event-pubsub))
  (when cache (kv/stop cache))
  (when event-store (es/stop event-store))
  (println "system stopped"))
