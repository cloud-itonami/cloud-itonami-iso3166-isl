(ns marketentry.marketentryllm
  "MarketEntry-LLM client -- the *contained intelligence node* for
  the ISL public-sector market-entry compliance actor.

  It normalizes engagement intake, drafts a per-jurisdiction market-
  entry evidence checklist, drafts the filing-draft action, and drafts
  the filing-submit action. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record or a real portal submission. Every
  output is censored downstream by `marketentry.governor` before
  anything touches the SSoT, and `:filing/draft`/`:filing/submit`
  proposals NEVER auto-commit at any phase -- see README Actuation.

  `:stale-authority?` on a `:jurisdiction/assess` request injects the
  failure mode this vertical exists to defend against: an LLM whose
  training data still says the decommissioned Ríkiskaup is Iceland's
  procurement authority, instead of the current Fjársýslan (FMA).
  `marketentry.governor`'s `stale-procurement-authority-violations`
  MUST catch this and HARD-hold it.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [marketentry.facts :as facts]
            [marketentry.store :as store]))

(defn- normalize-intake
  [_db {:keys [patch]}]
  {:summary    (str "参入案件記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :engagement/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction market-entry evidence checklist draft. `:no-spec?`
  injects the failure mode of proposing a checklist for a jurisdiction
  with NO official spec-basis. `:stale-authority?` injects the failure
  mode of citing the decommissioned Ríkiskaup instead of the current
  Fjársýslan (FMA)."
  [db {:keys [subject no-spec? stale-authority?]}]
  (let [e (store/engagement db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction e))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "marketentry.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)
                    :owner-authority (if stale-authority?
                                       "Ríkiskaup"
                                       (:owner-authority sb))}
       :stake      nil
       :confidence 0.9})))

(defn- propose-draft
  "Draft the actual FILING-DRAFT action. ALWAYS `:stake
  :actuation/draft-filing`."
  [db {:keys [subject]}]
  (let [e (store/engagement db subject)]
    {:summary    (str subject " 向け提出ドラフト提案"
                      (when e (str " (operator=" (:operator e) ")")))
     :rationale  (if e
                   (str "jurisdiction=" (:jurisdiction e)
                        " portal=" (:portal e))
                   "engagementが見つかりません")
     :cites      (if e [subject] [])
     :effect     :engagement/mark-drafted
     :value      {:engagement-id subject}
     :stake      :actuation/draft-filing
     :confidence (if e 0.9 0.3)}))

(defn- submit-self-check-ok?
  "The advisor's OWN imperfect self-assessment of whether a submit is
  likely clean -- a heuristic used only for the advisor's confidence
  score. The governor's `marketentry.governor/check` is the
  authoritative gate; this never substitutes for it."
  [e]
  (and e
       (or (not (:requires-vsk-registration? e)) (:vsk-registered? e))
       (or (not (:requires-vat-agent? e)) (:has-vat-agent? e))
       (or (not= (:sector e) :fisheries)
           (<= (:foreign-ownership-pct e 0)
               (facts/fisheries-cap-pct (:extended-conditions-met? e))))
       (let [sb (facts/sector-basis (:sector e))]
         (or (nil? sb) (not (:eea-exempt? sb))
             (:eea-established? e) (:nonresident-clearance-obtained? e)))))

(defn- propose-submit
  "Draft the actual FILING-SUBMIT action. ALWAYS `:stake
  :actuation/submit-filing` -- real-world portal submission."
  [db {:keys [subject]}]
  (let [e (store/engagement db subject)]
    {:summary    (str subject " 向けポータル提出提案"
                      (when e (str " (operator=" (:operator e) ")")))
     :rationale  (if e
                   (str "sector=" (:sector e)
                        " vsk-registered?=" (:vsk-registered? e)
                        " has-vat-agent?=" (:has-vat-agent? e)
                        " foreign-ownership-pct=" (:foreign-ownership-pct e)
                        " claimed-fee=" (:claimed-fee e))
                   "engagementが見つかりません")
     :cites      (if e [subject] [])
     :effect     :engagement/mark-submitted
     :value      {:engagement-id subject}
     :stake      :actuation/submit-filing
     :confidence (if (submit-self-check-ok? e) 0.9 0.3)}))

(defprotocol Advisor
  (-advise [this db request] "Return a proposal map for `request`."))

(defrecord MockAdvisor []
  Advisor
  (-advise [_ db {:keys [op] :as request}]
    (case op
      :engagement/intake    (normalize-intake db request)
      :jurisdiction/assess (assess-jurisdiction db request)
      :filing/draft        (propose-draft db request)
      :filing/submit       (propose-submit db request)
      {:summary "unknown op" :rationale "unsupported" :cites []
       :effect :noop :value {} :stake nil :confidence 0.0})))

(defn mock-advisor [] (->MockAdvisor))

(defn trace [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :summary (:summary proposal)
   :confidence (:confidence proposal)
   :stake (:stake proposal)})
