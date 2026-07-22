(ns marketentry.store
  "SSoT for the ISL market-entry compliance actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior cloud-itonami actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store.

  Both implement the same protocol and pass the same contract
  (test/marketentry/store_contract_test.clj).

  The primary entity here is an `engagement` -- filing-draft and
  filing-submit actuation events apply SEQUENTIALLY to the SAME
  engagement record (draft first, submit later). Dedicated
  double-actuation-guard booleans (`:drafted?`/`:submitted?`, never a
  `:status` value).

  The ledger stays append-only on every backend."
  (:require [marketentry.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (engagement [s id])
  (all-engagements [s])
  (assessment-of [s engagement-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (draft-history [s] "the append-only filing-draft history")
  (submit-history [s] "the append-only filing-submit history")
  (next-draft-sequence [s jurisdiction])
  (next-submit-sequence [s jurisdiction])
  (engagement-already-drafted? [s engagement-id])
  (engagement-already-submitted? [s engagement-id])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-engagements [s engagements] "replace/seed the engagement directory"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained engagement set covering both actuation
  lifecycles (draft, submit) plus every governor check this vertical
  adds: VSK/VAT registration, the non-resident VAT agent rule, the
  Act No. 34/1991 EEA-vs-non-EEA differentiated-treatment gate, and
  the fisheries hard cap (the one sector where EEA status does not
  waive the limit)."
  []
  {:engagements
   {"eng-1" {:id "eng-1" :operator "Nordur Digital ehf." :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 450000 :monthly-rate 20000 :monitoring-months 12
             :claimed-fee 690000.0
             :sector :general :eea-established? true :foreign-ownership-pct 0 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? true
             :requires-vat-agent? false :has-vat-agent? false
             :nonresident-clearance-obtained? true
             :drafted? false :submitted? false
             :jurisdiction "ISL" :status :intake}
    "eng-2" {:id "eng-2" :operator "Atlantis LLC" :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 450000 :monthly-rate 20000 :monitoring-months 12
             :claimed-fee 690000.0
             :sector :general :eea-established? true :foreign-ownership-pct 0 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? true
             :requires-vat-agent? false :has-vat-agent? false
             :nonresident-clearance-obtained? true
             :drafted? false :submitted? false
             :jurisdiction "ATL" :status :intake}
    "eng-3" {:id "eng-3" :operator "Minami Systems" :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 450000 :monthly-rate 20000 :monitoring-months 12
             :claimed-fee 999000.0
             :sector :general :eea-established? true :foreign-ownership-pct 0 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? true
             :requires-vat-agent? false :has-vat-agent? false
             :nonresident-clearance-obtained? true
             :drafted? false :submitted? false
             :jurisdiction "ISL" :status :intake}
    "eng-4" {:id "eng-4" :operator "Higashi Export" :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 450000 :monthly-rate 20000 :monitoring-months 12
             :claimed-fee 690000.0
             :sector :general :eea-established? true :foreign-ownership-pct 0 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? false
             :requires-vat-agent? false :has-vat-agent? false
             :nonresident-clearance-obtained? true
             :drafted? false :submitted? false
             :jurisdiction "ISL" :status :intake}
    "eng-5" {:id "eng-5" :operator "Nishi Logistics" :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 450000 :monthly-rate 20000 :monitoring-months 12
             :claimed-fee 690000.0
             :sector :general :eea-established? false :foreign-ownership-pct 0 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? true
             :requires-vat-agent? true :has-vat-agent? false
             :nonresident-clearance-obtained? true
             :drafted? false :submitted? false
             :jurisdiction "ISL" :status :intake}
    "eng-6" {:id "eng-6" :operator "Vestan Seafood ehf." :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 400000 :monthly-rate 15000 :monitoring-months 6
             :claimed-fee 490000.0
             :sector :fisheries :eea-established? false :foreign-ownership-pct 40 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? true
             :requires-vat-agent? true :has-vat-agent? true
             :nonresident-clearance-obtained? true
             :drafted? false :submitted? false
             :jurisdiction "ISL" :status :intake}
    "eng-7" {:id "eng-7" :operator "Sudur Fisheries ehf." :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 400000 :monthly-rate 15000 :monitoring-months 6
             :claimed-fee 490000.0
             :sector :fisheries :eea-established? true :foreign-ownership-pct 20 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? true
             :requires-vat-agent? false :has-vat-agent? false
             :nonresident-clearance-obtained? true
             :drafted? false :submitted? false
             :jurisdiction "ISL" :status :intake}
    "eng-8" {:id "eng-8" :operator "Baltic Marine Holdings" :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 400000 :monthly-rate 15000 :monitoring-months 6
             :claimed-fee 490000.0
             :sector :fisheries :eea-established? true :foreign-ownership-pct 40 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? true
             :requires-vat-agent? false :has-vat-agent? false
             :nonresident-clearance-obtained? true
             :drafted? false :submitted? false
             :jurisdiction "ISL" :status :intake}
    "eng-9" {:id "eng-9" :operator "Austur Property Group" :portal "Fjársýslan (FMA) e-procurement"
             :base-fee 450000 :monthly-rate 20000 :monitoring-months 12
             :claimed-fee 690000.0
             :sector :real-estate :eea-established? false :foreign-ownership-pct 100 :extended-conditions-met? false
             :requires-vsk-registration? true :vsk-registered? true
             :requires-vat-agent? true :has-vat-agent? true
             :nonresident-clearance-obtained? false
             :drafted? false :submitted? false
             :jurisdiction "ISL" :status :intake}
    "eng-10" {:id "eng-10" :operator "Air Nordic Charter" :portal "Fjársýslan (FMA) e-procurement"
              :base-fee 450000 :monthly-rate 20000 :monitoring-months 12
              :claimed-fee 690000.0
              :sector :aviation :eea-established? true :foreign-ownership-pct 60 :extended-conditions-met? false
              :requires-vsk-registration? true :vsk-registered? true
              :requires-vat-agent? false :has-vat-agent? false
              :nonresident-clearance-obtained? false
              :drafted? false :submitted? false
              :jurisdiction "ISL" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- draft-filing!
  [s engagement-id]
  (let [e (engagement s engagement-id)
        seq-n (next-draft-sequence s (:jurisdiction e))
        result (registry/register-draft engagement-id (:jurisdiction e) seq-n)]
    {:result result
     :engagement-patch {:drafted? true
                        :draft-number (get result "draft_number")}}))

(defn- submit-filing!
  [s engagement-id]
  (let [e (engagement s engagement-id)
        seq-n (next-submit-sequence s (:jurisdiction e))
        result (registry/register-submit engagement-id (:jurisdiction e) seq-n)]
    {:result result
     :engagement-patch {:submitted? true
                        :submit-number (get result "submit_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (engagement [_ id] (get-in @a [:engagements id]))
  (all-engagements [_] (sort-by :id (vals (:engagements @a))))
  (assessment-of [_ engagement-id] (get-in @a [:assessments engagement-id]))
  (ledger [_] (:ledger @a))
  (draft-history [_] (:draft-records @a))
  (submit-history [_] (:submit-records @a))
  (next-draft-sequence [_ jurisdiction] (get-in @a [:draft-sequences jurisdiction] 0))
  (next-submit-sequence [_ jurisdiction] (get-in @a [:submit-sequences jurisdiction] 0))
  (engagement-already-drafted? [_ engagement-id] (boolean (get-in @a [:engagements engagement-id :drafted?])))
  (engagement-already-submitted? [_ engagement-id] (boolean (get-in @a [:engagements engagement-id :submitted?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (swap! a update-in [:engagements (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :engagement/mark-drafted
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (draft-filing! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:draft-sequences jurisdiction] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :draft-records registry/append result))))
        result)

      :engagement/mark-submitted
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (submit-filing! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:submit-sequences jurisdiction] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :submit-records registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-engagements [s engagements] (when (seq engagements) (swap! a assoc :engagements engagements)) s))

(defn seed-db
  "A MemStore seeded with the demo engagement set."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :draft-sequences {} :draft-records []
                           :submit-sequences {} :submit-records []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  {:engagement/id                   {:db/unique :db.unique/identity}
   :assessment/engagement-id        {:db/unique :db.unique/identity}
   :ledger/seq                      {:db/unique :db.unique/identity}
   :draft-record/seq                {:db/unique :db.unique/identity}
   :submit-record/seq               {:db/unique :db.unique/identity}
   :draft-sequence/jurisdiction     {:db/unique :db.unique/identity}
   :submit-sequence/jurisdiction    {:db/unique :db.unique/identity}})

(defn- engagement->tx [{:keys [id operator portal base-fee monthly-rate monitoring-months claimed-fee
                               sector eea-established? foreign-ownership-pct extended-conditions-met?
                               requires-vsk-registration? vsk-registered?
                               requires-vat-agent? has-vat-agent?
                               nonresident-clearance-obtained?
                               drafted? submitted?
                               jurisdiction status draft-number submit-number]}]
  (cond-> {:engagement/id id}
    operator                                   (assoc :engagement/operator operator)
    portal                                     (assoc :engagement/portal portal)
    base-fee                                   (assoc :engagement/base-fee base-fee)
    monthly-rate                               (assoc :engagement/monthly-rate monthly-rate)
    monitoring-months                          (assoc :engagement/monitoring-months monitoring-months)
    claimed-fee                                (assoc :engagement/claimed-fee claimed-fee)
    sector                                     (assoc :engagement/sector sector)
    (some? eea-established?)         (assoc :engagement/eea-established? eea-established?)
    foreign-ownership-pct                      (assoc :engagement/foreign-ownership-pct foreign-ownership-pct)
    (some? extended-conditions-met?) (assoc :engagement/extended-conditions-met? extended-conditions-met?)
    (some? requires-vsk-registration?) (assoc :engagement/requires-vsk-registration? requires-vsk-registration?)
    (some? vsk-registered?)          (assoc :engagement/vsk-registered? vsk-registered?)
    (some? requires-vat-agent?)      (assoc :engagement/requires-vat-agent? requires-vat-agent?)
    (some? has-vat-agent?)           (assoc :engagement/has-vat-agent? has-vat-agent?)
    (some? nonresident-clearance-obtained?) (assoc :engagement/nonresident-clearance-obtained? nonresident-clearance-obtained?)
    (some? drafted?)                           (assoc :engagement/drafted? drafted?)
    (some? submitted?)                         (assoc :engagement/submitted? submitted?)
    jurisdiction                               (assoc :engagement/jurisdiction jurisdiction)
    status                                      (assoc :engagement/status status)
    draft-number                               (assoc :engagement/draft-number draft-number)
    submit-number                              (assoc :engagement/submit-number submit-number)))

(def ^:private engagement-pull
  [:engagement/id :engagement/operator :engagement/portal :engagement/base-fee :engagement/monthly-rate
   :engagement/monitoring-months :engagement/claimed-fee
   :engagement/sector :engagement/eea-established? :engagement/foreign-ownership-pct :engagement/extended-conditions-met?
   :engagement/requires-vsk-registration? :engagement/vsk-registered?
   :engagement/requires-vat-agent? :engagement/has-vat-agent?
   :engagement/nonresident-clearance-obtained?
   :engagement/drafted? :engagement/submitted?
   :engagement/jurisdiction :engagement/status :engagement/draft-number :engagement/submit-number])

(defn- pull->engagement [m]
  (when (:engagement/id m)
    {:id (:engagement/id m) :operator (:engagement/operator m) :portal (:engagement/portal m)
     :base-fee (:engagement/base-fee m) :monthly-rate (:engagement/monthly-rate m)
     :monitoring-months (:engagement/monitoring-months m) :claimed-fee (:engagement/claimed-fee m)
     :sector (:engagement/sector m)
     :eea-established? (boolean (:engagement/eea-established? m))
     :foreign-ownership-pct (:engagement/foreign-ownership-pct m)
     :extended-conditions-met? (boolean (:engagement/extended-conditions-met? m))
     :requires-vsk-registration? (boolean (:engagement/requires-vsk-registration? m))
     :vsk-registered? (boolean (:engagement/vsk-registered? m))
     :requires-vat-agent? (boolean (:engagement/requires-vat-agent? m))
     :has-vat-agent? (boolean (:engagement/has-vat-agent? m))
     :nonresident-clearance-obtained? (boolean (:engagement/nonresident-clearance-obtained? m))
     :drafted? (boolean (:engagement/drafted? m)) :submitted? (boolean (:engagement/submitted? m))
     :jurisdiction (:engagement/jurisdiction m) :status (:engagement/status m)
     :draft-number (:engagement/draft-number m) :submit-number (:engagement/submit-number m)}))

(defrecord DatomicStore [conn]
  Store
  (engagement [_ id]
    (pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id id])))
  (all-engagements [_]
    (->> (d/q '[:find [?id ...] :where [?e :engagement/id ?id]] (d/db conn))
         (map #(pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id %])))
         (sort-by :id)))
  (assessment-of [_ engagement-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?eid
                   :where [?a :assessment/engagement-id ?eid] [?a :assessment/payload ?p]]
                 (d/db conn) engagement-id)))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (draft-history [_] (ls/read-stream conn :draft-record/seq :draft-record/record))
  (submit-history [_] (ls/read-stream conn :submit-record/seq :submit-record/record))
  (next-draft-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :draft-sequence/jurisdiction ?j] [?e :draft-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-submit-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :submit-sequence/jurisdiction ?j] [?e :submit-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (engagement-already-drafted? [s engagement-id]
    (boolean (:drafted? (engagement s engagement-id))))
  (engagement-already-submitted? [s engagement-id]
    (boolean (:submitted? (engagement s engagement-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (d/transact! conn [(engagement->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/engagement-id (first path) :assessment/payload (ls/enc payload)}])

      :engagement/mark-drafted
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (draft-filing! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))
            next-n (inc (next-draft-sequence s jurisdiction))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:draft-sequence/jurisdiction jurisdiction :draft-sequence/next next-n}
                      {:draft-record/seq (count (draft-history s)) :draft-record/record (ls/enc (get result "record"))}])
        result)

      :engagement/mark-submitted
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (submit-filing! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))
            next-n (inc (next-submit-sequence s jurisdiction))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:submit-sequence/jurisdiction jurisdiction :submit-sequence/next next-n}
                      {:submit-record/seq (count (submit-history s)) :submit-record/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger s)) fact)
    fact)
  (with-engagements [s engagements]
    (when (seq engagements) (d/transact! conn (mapv engagement->tx (vals engagements)))) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([{:keys [engagements]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-engagements s engagements))))

(defn datomic-seed-db
  []
  (datomic-store (demo-data)))
