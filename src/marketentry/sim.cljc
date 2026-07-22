(ns marketentry.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean engagement
  through intake -> jurisdiction assessment -> filing draft
  (escalate/approve/commit) -> filing submit (escalate/approve/
  commit), then shows every HARD-hold scenario this vertical adds:
  stale procurement authority, VSK/VAT unregistered, missing VAT
  agent, missing Act No. 34/1991 non-resident clearance, and the
  fisheries cap (which fires even for an EEA-established operator)."
  (:require [langgraph.graph :as g]
            [marketentry.store :as store]
            [marketentry.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :market-entry-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== engagement/intake eng-1 (ISL, clean) ==")
    (println (exec-op actor "t1" {:op :engagement/intake :subject "eng-1"
                                  :patch {:id "eng-1" :operator "Nordur Digital ehf."}} operator))

    (println "== jurisdiction/assess eng-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :jurisdiction/assess :subject "eng-1"} operator))
    (println (approve! actor "t2"))

    (println "== filing/draft eng-1 (always escalates -- actuation/draft-filing) ==")
    (let [r (exec-op actor "t3" {:op :filing/draft :subject "eng-1"} operator)]
      (println r)
      (println "-- human market-entry operator approves --")
      (println (approve! actor "t3")))

    (println "== filing/submit eng-1 (always escalates -- actuation/submit-filing) ==")
    (let [r (exec-op actor "t4" {:op :filing/submit :subject "eng-1"} operator)]
      (println r)
      (println "-- human market-entry operator approves --")
      (println (approve! actor "t4")))

    (println "== jurisdiction/assess eng-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :jurisdiction/assess :subject "eng-2" :no-spec? true} operator))

    (println "== jurisdiction/assess eng-1-b (stale-authority injected -> HARD hold) ==")
    (println (exec-op actor "t5b" {:op :jurisdiction/assess :subject "eng-1" :stale-authority? true} operator))

    (println "== jurisdiction/assess eng-3 (sets up fee-mismatch) ==")
    (println (exec-op actor "t6" {:op :jurisdiction/assess :subject "eng-3"} operator))
    (println (approve! actor "t6"))
    (println (exec-op actor "t6b" {:op :filing/draft :subject "eng-3"} operator))
    (println (approve! actor "t6b"))
    (println "== filing/submit eng-3 (fee mismatch -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :filing/submit :subject "eng-3"} operator))

    (println "== jurisdiction/assess eng-4 (sets up vsk-unregistered) ==")
    (println (exec-op actor "t8" {:op :jurisdiction/assess :subject "eng-4"} operator))
    (println (approve! actor "t8"))
    (println (exec-op actor "t8b" {:op :filing/draft :subject "eng-4"} operator))
    (println (approve! actor "t8b"))
    (println "== filing/submit eng-4 (vsk-unregistered -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :filing/submit :subject "eng-4"} operator))

    (println "== jurisdiction/assess eng-5 (sets up vat-agent-missing) ==")
    (println (exec-op actor "t10" {:op :jurisdiction/assess :subject "eng-5"} operator))
    (println (approve! actor "t10"))
    (println (exec-op actor "t10b" {:op :filing/draft :subject "eng-5"} operator))
    (println (approve! actor "t10b"))
    (println "== filing/submit eng-5 (vat-agent-missing -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :filing/submit :subject "eng-5"} operator))

    (println "== jurisdiction/assess eng-6 (sets up fisheries-cap-exceeded, non-EEA) ==")
    (println (exec-op actor "t12" {:op :jurisdiction/assess :subject "eng-6"} operator))
    (println (approve! actor "t12"))
    (println (exec-op actor "t12b" {:op :filing/draft :subject "eng-6"} operator))
    (println (approve! actor "t12b"))
    (println "== filing/submit eng-6 (fisheries-cap-exceeded -> HARD hold) ==")
    (println (exec-op actor "t13" {:op :filing/submit :subject "eng-6"} operator))

    (println "== jurisdiction/assess eng-8 (sets up fisheries-cap-exceeded despite EEA) ==")
    (println (exec-op actor "t14" {:op :jurisdiction/assess :subject "eng-8"} operator))
    (println (approve! actor "t14"))
    (println (exec-op actor "t14b" {:op :filing/draft :subject "eng-8"} operator))
    (println (approve! actor "t14b"))
    (println "== filing/submit eng-8 (EEA-established but fisheries cap STILL exceeded -> HARD hold) ==")
    (println (exec-op actor "t15" {:op :filing/submit :subject "eng-8"} operator))

    (println "== jurisdiction/assess eng-9 (sets up nonresident-clearance-missing) ==")
    (println (exec-op actor "t16" {:op :jurisdiction/assess :subject "eng-9"} operator))
    (println (approve! actor "t16"))
    (println (exec-op actor "t16b" {:op :filing/draft :subject "eng-9"} operator))
    (println (approve! actor "t16b"))
    (println "== filing/submit eng-9 (real-estate, non-EEA, no clearance -> HARD hold) ==")
    (println (exec-op actor "t17" {:op :filing/submit :subject "eng-9"} operator))

    (println "== filing/draft eng-1 AGAIN (double-draft -> HARD hold) ==")
    (println (exec-op actor "t18" {:op :filing/draft :subject "eng-1"} operator))

    (println "== filing/submit eng-1 AGAIN (double-submit -> HARD hold) ==")
    (println (exec-op actor "t19" {:op :filing/submit :subject "eng-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft records ==")
    (doseq [r (store/draft-history db)] (println r))

    (println "== submit records ==")
    (doseq [r (store/submit-history db)] (println r))))
