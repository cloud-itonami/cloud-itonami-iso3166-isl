(ns marketentry.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [marketentry.facts :as facts]))

(deftest isl-has-spec-basis
  (let [sb (facts/spec-basis "ISL")]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (some? (facts/rep-spec-basis "ISL")))
    (is (some? (facts/corporate-number-spec-basis "ISL")))))

(deftest unknown-jurisdiction-has-no-spec-basis
  (is (nil? (facts/spec-basis "ATL")))
  (is (nil? (facts/spec-basis "ZZZ"))))

(deftest required-evidence-satisfied
  (let [sb (facts/spec-basis "ISL")
        all (:required-evidence sb)]
    (is (true? (facts/required-evidence-satisfied? "ISL" all)))
    (is (not (facts/required-evidence-satisfied? "ISL" (take 1 all))))
    (is (nil? (facts/required-evidence-satisfied? "ATL" all)))))

(deftest coverage-is-honest
  (let [c (facts/coverage ["ISL" "USA" "ATL"])]
    (is (= 3 (:requested c)))
    (is (= 2 (:covered c)))
    (is (= ["ATL"] (:missing-jurisdictions c)))))

(deftest current-procurement-authority-is-fjarsyslan-not-rikiskaup
  (testing "the flagship currency fact: Ríkiskaup was decommissioned 2024-08-01"
    (is (= "Fjársýslan (FMA)" facts/procurement-authority-current))
    (is (= "Fjársýslan (FMA)" (:owner-authority (facts/spec-basis "ISL"))))
    (is (not (facts/stale-procurement-authority? "Fjársýslan (FMA)")))))

(deftest stale-procurement-authority-detection
  (is (true? (facts/stale-procurement-authority? "Ríkiskaup")))
  (is (true? (facts/stale-procurement-authority? "Rikiskaup")))
  (is (false? (facts/stale-procurement-authority? "Fjársýslan (FMA)")))
  (is (false? (facts/stale-procurement-authority? nil))))

(deftest sector-restrictions-are-not-flattened
  (testing "fisheries is the ONE sector where EEA status does NOT exempt"
    (is (false? (facts/sector-eea-exempt? :fisheries)))
    (is (true? (facts/sector-eea-exempt? :energy)))
    (is (true? (facts/sector-eea-exempt? :aviation)))
    (is (true? (facts/sector-eea-exempt? :real-estate)))
    (is (nil? (facts/sector-basis :general)) "unrestricted sectors carry no record")))

(deftest fisheries-cap-pct-honors-extension
  (is (= 25 (facts/fisheries-cap-pct false)))
  (is (= 33 (facts/fisheries-cap-pct true))))
