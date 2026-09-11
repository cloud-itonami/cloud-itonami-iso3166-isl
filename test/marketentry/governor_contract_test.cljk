(ns marketentry.governor-contract-test
  "The governor contract as executable tests -- this vertical's own
  Trust Controls implemented faithfully. The core invariant under test:

    MarketEntry-LLM never drafts or submits a filing the Market-Entry
    Compliance Governor would reject, `:filing/draft`/`:filing/submit`
    NEVER auto-commit at any phase, `:engagement/intake` MAY auto-commit
    when clean, and every decision (commit OR hold) leaves exactly one
    ledger fact.

  Plus the checks specific to this vertical: a stale procurement
  authority (Ríkiskaup, decommissioned 2024-08-01) is a HARD hold;
  missing VSK/VAT registration or a missing non-resident VAT agent is
  a HARD hold; the Act No. 34/1991 non-resident-clearance gate applies
  to energy/aviation/real-estate but NOT fisheries; and the fisheries
  ownership cap fires REGARDLESS of EEA-established status -- the one
  sector where EEA status does not waive the limit."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [marketentry.store :as store]
            [marketentry.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :market-entry-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- draft!
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-draft") {:op :filing/draft :subject subject} operator)
  (approve! actor (str tid-prefix "-draft")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :engagement/intake :subject "eng-1"
                   :patch {:id "eng-1" :operator "Nordur Digital ehf."}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Nordur Digital ehf." (:operator (store/engagement db "eng-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "eng-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "eng-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "eng-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "eng-1")) "no assessment written"))))

(deftest draft-without-assessment-is-held
  (testing "filing/draft before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :filing/draft :subject "eng-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest stale-procurement-authority-is-held-and-unoverridable
  (testing "citing the decommissioned Ríkiskaup instead of Fjársýslan (FMA) -> HARD hold"
    (let [[db actor] (fresh)
          res (exec-op actor "t5"
                    {:op :jurisdiction/assess :subject "eng-1" :stale-authority? true} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:stale-procurement-authority} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "eng-1")) "no assessment written on a stale-authority proposal"))))

(deftest fresh-assess-cites-fjarsyslan-not-rikiskaup
  (testing "a clean assess (no injected failure mode) cites the CURRENT authority"
    (let [[db actor] (fresh)]
      (assess! actor "t5c" "eng-1")
      (is (= "Fjársýslan (FMA)" (:owner-authority (store/assessment-of db "eng-1")))))))

(deftest vsk-unregistered-is-held-and-unoverridable
  (testing "missing VSK/VAT registration -> HARD hold"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre" "eng-4")
          _ (draft! actor "t6pre" "eng-4")
          res (exec-op actor "t6" {:op :filing/submit :subject "eng-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:vsk-unregistered} (-> (store/ledger db) last :basis)))
      (is (empty? (store/submit-history db))))))

(deftest vat-agent-missing-is-held-and-unoverridable
  (testing "missing non-resident VAT agent -> HARD hold"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "eng-5")
          _ (draft! actor "t7pre" "eng-5")
          res (exec-op actor "t7" {:op :filing/submit :subject "eng-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:vat-agent-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/submit-history db))))))

(deftest fisheries-cap-exceeded-is-held-and-unoverridable
  (testing "fisheries foreign-ownership above 25% cap, non-EEA -> HARD hold"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "eng-6")
          _ (draft! actor "t8pre" "eng-6")
          res (exec-op actor "t8" {:op :filing/submit :subject "eng-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (some #{:fisheries-cap-exceeded} (-> (store/ledger db) last :basis)))
      (is (empty? (store/submit-history db))))))

(deftest fisheries-cap-exceeded-even-for-eea-established
  (testing "FLAGSHIP: fisheries is the ONE sector where EEA-established status does NOT waive the cap"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "eng-8")
          _ (draft! actor "t9pre" "eng-8")
          res (exec-op actor "t9" {:op :filing/submit :subject "eng-8"} operator)]
      (is (true? (:eea-established? (store/engagement db "eng-8"))) "operator IS EEA-established")
      (is (= :hold (get-in res [:state :disposition])) "still HARD hold -- EEA status does not exempt fisheries")
      (is (some #{:fisheries-cap-exceeded} (-> (store/ledger db) last :basis)))
      (is (empty? (store/submit-history db))))))

(deftest fisheries-within-cap-is-clean
  (testing "20% foreign ownership, under the 25% cap -> no fisheries-cap-exceeded violation"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "eng-7")
          _ (draft! actor "t10pre" "eng-7")
          r1 (exec-op actor "t10" {:op :filing/submit :subject "eng-7"} operator)]
      (is (= :interrupted (:status r1)) "clean submit still always escalates for human approval")
      (let [r2 (approve! actor "t10")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:submitted? (store/engagement db "eng-7"))))))))

(deftest nonresident-clearance-missing-is-held
  (testing "real-estate, non-EEA-established, no Act 34/1991 clearance -> HARD hold"
    (let [[db actor] (fresh)
          _ (assess! actor "t11pre" "eng-9")
          _ (draft! actor "t11pre" "eng-9")
          res (exec-op actor "t11" {:op :filing/submit :subject "eng-9"} operator)]
      (is (false? (:eea-established? (store/engagement db "eng-9"))))
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:nonresident-clearance-missing} (-> (store/ledger db) last :basis)))
      (is (not (some #{:fisheries-cap-exceeded} (-> (store/ledger db) last :basis)))
          "fisheries check must never fire for a non-fisheries (real-estate) engagement")
      (is (empty? (store/submit-history db))))))

(deftest eea-established-exempts-nonresident-clearance
  (testing "aviation, EEA-established, no clearance recorded -> exempt, no violation (EEA status DOES waive this gate)"
    (let [[db actor] (fresh)
          _ (assess! actor "t12pre" "eng-10")
          _ (draft! actor "t12pre" "eng-10")
          r1 (exec-op actor "t12" {:op :filing/submit :subject "eng-10"} operator)]
      (is (true? (:eea-established? (store/engagement db "eng-10"))))
      (is (= :interrupted (:status r1)) "clean submit still always escalates for human approval")
      (let [r2 (approve! actor "t12")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:submitted? (store/engagement db "eng-10"))))))))

(deftest engagement-fee-mismatch-is-held
  (testing "claimed fee that doesn't equal base + months x rate -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t13pre" "eng-3")
          _ (draft! actor "t13pre" "eng-3")
          res (exec-op actor "t13" {:op :filing/submit :subject "eng-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:engagement-fee-mismatch} (-> (store/ledger db) last :basis)))
      (is (empty? (store/submit-history db))))))

(deftest submit-always-escalates-then-human-decides
  (testing "a clean fully-assessed submit still ALWAYS interrupts for human approval"
    (let [[db actor] (fresh)
          _ (assess! actor "t14pre" "eng-1")
          _ (draft! actor "t14pre" "eng-1")
          r1 (exec-op actor "t14" {:op :filing/submit :subject "eng-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, submit record drafted"
        (let [r2 (approve! actor "t14")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:submitted? (store/engagement db "eng-1"))))
          (is (= 1 (count (store/submit-history db))) "one draft submit record"))))))

(deftest draft-always-escalates-then-human-decides
  (testing "a clean fully-assessed draft still ALWAYS interrupts for human approval"
    (let [[db actor] (fresh)
          _ (assess! actor "t15pre" "eng-1")
          r1 (exec-op actor "t15" {:op :filing/draft :subject "eng-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, draft record drafted"
        (let [r2 (approve! actor "t15")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:drafted? (store/engagement db "eng-1"))))
          (is (= 1 (count (store/draft-history db))) "one draft record"))))))

(deftest engagement-double-draft-is-held
  (testing "drafting the same engagement twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t16pre" "eng-1")
          _ (draft! actor "t16pre" "eng-1")
          res (exec-op actor "t16" {:op :filing/draft :subject "eng-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-drafted} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/draft-history db))) "still only the one earlier draft"))))

(deftest engagement-double-submit-is-held
  (testing "submitting the same engagement twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t17pre" "eng-1")
          _ (draft! actor "t17pre" "eng-1")
          _ (exec-op actor "t17a" {:op :filing/submit :subject "eng-1"} operator)
          _ (approve! actor "t17a")
          res (exec-op actor "t17" {:op :filing/submit :subject "eng-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-submitted} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/submit-history db))) "still only the one earlier submit"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :engagement/intake :subject "eng-1"
                          :patch {:id "eng-1" :operator "Nordur Digital ehf."}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "eng-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))

(deftest ledger-is-append-only
  (testing "ledger only grows -- no prior fact is ever removed or overwritten"
    (let [[db actor] (fresh)]
      (exec-op actor "l1" {:op :engagement/intake :subject "eng-1"
                           :patch {:id "eng-1" :operator "Nordur Digital ehf."}} operator)
      (let [before (store/ledger db)]
        (exec-op actor "l2" {:op :jurisdiction/assess :subject "eng-1" :no-spec? true} operator)
        (let [after (store/ledger db)]
          (is (= before (take (count before) after)) "prior facts are untouched, only appended-to")
          (is (> (count after) (count before))))))))
