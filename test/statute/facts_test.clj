(ns statute.facts-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [statute.facts :as facts]))

(deftest isl-has-spec-basis
  (let [sb (facts/spec-basis "ISL")]
    (is (= 3 (count sb)))
    (is (every? #(str/starts-with? (:statute/url %) "https://www.althingi.is/") sb))
    (is (every? :statute/law-number sb))))

(deftest unknown-jurisdiction-has-no-spec-basis
  (is (nil? (facts/spec-basis "ATL")))
  (is (nil? (facts/spec-basis "ZZZ"))))

(deftest coverage-is-honest
  (let [c (facts/coverage ["ISL" "JPN" "ATL"])]
    (is (= 3 (:requested c)))
    (is (= 1 (:covered c)))
    (is (= ["ATL" "JPN"] (:missing-jurisdictions c)))))

(deftest by-topic-filters
  (is (= ["isl.vinnuverndarlog-1980"]
         (mapv :statute/id (facts/by-topic "ISL" :labor))))
  (is (empty? (facts/by-topic "ISL" :environment)))
  (is (empty? (facts/by-topic "ATL" :labor))))

(deftest statute-catalog-does-not-reintroduce-stale-authority-name
  (testing "this catalog is orthogonal to marketentry.facts's procurement-
  authority currency trap and must never cite the decommissioned Ríkiskaup"
    (let [sb (facts/spec-basis "ISL")
          blob (pr-str sb)]
      (is (not (str/includes? blob "Ríkiskaup")))
      (is (not (str/includes? blob "Rikiskaup"))))))
