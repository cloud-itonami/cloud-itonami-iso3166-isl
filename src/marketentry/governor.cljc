(ns marketentry.governor
  "Market-Entry Compliance Governor -- the independent compliance layer
  that earns the MarketEntry-LLM the right to commit. The LLM has no
  notion of Icelandic procurement law, whether Fjársýslan (not the
  decommissioned Ríkiskaup) is actually the authority it should cite,
  whether a foreign operator's VSK/VAT registration or non-resident
  agent is actually on file, whether an engagement in the fisheries
  sector actually respects the one ownership cap EEA status does NOT
  waive, whether a claimed engagement fee actually equals base +
  months x rate, or when a draft stops being a draft and becomes a
  real-world portal submission, so this MUST be a separate system able
  to *reject* a proposal and fall back to HOLD.

  `:itonami.blueprint/governor` is `:market-entry-compliance-governor`
  (shared family keyword on blueprints; this is a running
  implementation of that governor for the iso3166 family).

  This blueprint's own text (docs/business-model.md Trust Controls:
  'any actual portal registration or filing submission requires
  Market-Entry Compliance Governor clearance and always escalates to
  human sign-off'; 'a false or fabricated regulatory-requirement claim
  is a HARD hold') names exactly the checks below.

  All HARD violations below: a human approver CANNOT override them.
  The confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `marketentry.phase`: for `:stake :actuation/draft-filing`/
  `:actuation/submit-filing` NO phase ever allows auto-commit either.
  Two independent layers agree that actuation is always a human call.

    1. Spec-basis                     -- did the jurisdiction proposal
                                          cite an OFFICIAL source
                                          (`marketentry.facts`), or
                                          invent one?
    2. Evidence incomplete            -- for `:filing/draft`/
                                          `:filing/submit`, has the
                                          jurisdiction actually been
                                          assessed with a full evidence
                                          checklist on file?
    3. Stale procurement authority    -- FLAGSHIP currency check: does
                                          a `:jurisdiction/assess`/
                                          `:filing/draft`/`:filing/
                                          submit` proposal cite the
                                          DECOMMISSIONED Ríkiskaup
                                          (superseded 2024-08-01 by
                                          Fjársýslan/FMA) as the
                                          procurement authority? An
                                          LLM's training data is
                                          plausibly stale here -- this
                                          negative/currency check
                                          exists specifically to catch
                                          that.
    4. VSK/VAT unregistered           -- for `:filing/submit`, when the
                                          engagement declares
                                          `:requires-vsk-registration?
                                          true` (near-universal --
                                          Iceland has NO registration
                                          threshold, foreign entities
                                          must register from the first
                                          taxable supply),
                                          INDEPENDENTLY verify
                                          `:vsk-registered?` is true.
    5. VAT agent missing              -- for `:filing/submit`, when the
                                          engagement declares
                                          `:requires-vat-agent? true`
                                          (a foreign company without a
                                          permanent establishment must
                                          appoint a locally domiciled
                                          agent for VAT notification/
                                          collection/remittance),
                                          INDEPENDENTLY verify
                                          `:has-vat-agent?` is true.
    6. Non-resident clearance missing -- for `:filing/submit`, the
                                          general Act No. 34/1991
                                          EEA-vs-non-EEA gate: when the
                                          engagement's own `:sector` is
                                          one of the restricted sectors
                                          that DOES exempt EEA-
                                          established operators
                                          (energy/aviation/real-estate)
                                          and the engagement's own
                                          `:eea-established?` is false,
                                          INDEPENDENTLY verify
                                          `:nonresident-clearance-
                                          obtained?` is true. The
                                          *requirement itself* is
                                          computed from
                                          `marketentry.facts/sector-
                                          basis`, not merely trusted
                                          off an engagement-declared
                                          flag.
    7. Fisheries cap exceeded         -- for `:filing/submit`, when the
                                          engagement's own `:sector` is
                                          `:fisheries` -- the ONE
                                          sector where EEA status does
                                          NOT waive the limit --
                                          INDEPENDENTLY recompute the
                                          applicable cap (25%, or 33%
                                          only when the engagement's
                                          own `:extended-conditions-
                                          met?` is true) and HARD-hold
                                          if `:foreign-ownership-pct`
                                          exceeds it, REGARDLESS of
                                          `:eea-established?`. Never
                                          fires for a non-fisheries
                                          engagement.
    8. Engagement fee mismatch        -- for `:filing/submit`,
                                          INDEPENDENTLY recompute
                                          whether the engagement's own
                                          `:claimed-fee` equals
                                          `base-fee + monthly-rate x
                                          monitoring-months` -- honest
                                          reapplication of the ground-
                                          truth-recompute discipline
                                          sibling actors use.
    9. Confidence floor / actuation
       gate                           -- LLM confidence below
                                          threshold, OR the op is
                                          `:filing/draft`/`:filing/
                                          submit` (REAL acts) ->
                                          escalate.

  Two more guards, double-draft/double-submit prevention, are enforced
  off dedicated `:drafted?`/`:submitted?` facts (never a `:status`
  value)."
  (:require [marketentry.facts :as facts]
            [marketentry.registry :as registry]
            [marketentry.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Drafting a real portal package and submitting a real portal
  registration are the two real-world actuation events this actor
  performs."
  #{:actuation/draft-filing :actuation/submit-filing})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:filing/draft`/`:filing/submit`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's market-entry requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :filing/draft :filing/submit} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:filing/draft`/`:filing/submit`, the jurisdiction's required
  registration evidence must actually be satisfied."
  [{:keys [op subject]} st]
  (when (contains? #{:filing/draft :filing/submit} op)
    (let [e (store/engagement st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction e) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(Fyrirtækjaskrá登記/VSK登録/Fjársýslan e-procurement登録/代理人確認等)が充足していない状態での提案"}]))))

(defn- stale-procurement-authority-violations
  "FLAGSHIP currency check: a `:jurisdiction/assess`/`:filing/draft`/
  `:filing/submit` proposal that cites the DECOMMISSIONED Ríkiskaup
  (superseded 2024-08-01 by Fjársýslan/FMA) as the procurement
  authority is stale training data, not a live fact -- HARD hold,
  regardless of confidence."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :filing/draft :filing/submit} op)
    (let [authority (:owner-authority (:value proposal))]
      (when (facts/stale-procurement-authority? authority)
        [{:rule :stale-procurement-authority
          :detail (str "調達当局として \"" authority "\" が引用されたが、これは2024-08-01付で"
                       "Fjársýslan(FMA)に統合され廃止された旧当局 -- 現在の当局はFjársýslan(FMA)")}]))))

(defn- vsk-unregistered-violations
  "For `:filing/submit`, when the engagement declares
  `:requires-vsk-registration? true`, INDEPENDENTLY check
  `:vsk-registered?` -- CONDITIONAL on the engagement's own ground
  truth. Iceland has NO VAT registration threshold; a foreign entity
  making taxable supplies must register from the FIRST taxable
  supply."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)]
      (when (and (true? (:requires-vsk-registration? e))
                 (not (true? (:vsk-registered? e))))
        [{:rule :vsk-unregistered
          :detail (str subject " はVSK/VAT登録(RSK 5.02)を要するが未登録 -- 提出提案は進められない")}]))))

(defn- vat-agent-missing-violations
  "For `:filing/submit`, when the engagement declares
  `:requires-vat-agent? true` (a foreign company without a permanent
  establishment must appoint a locally domiciled agent for VAT
  notification/collection/remittance), INDEPENDENTLY check
  `:has-vat-agent?`."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)]
      (when (and (true? (:requires-vat-agent? e))
                 (not (true? (:has-vat-agent? e))))
        [{:rule :vat-agent-missing
          :detail (str subject " は現地居住VAT代理人の選任を要するが未確認 -- 提出提案は進められない")}]))))

(defn- nonresident-clearance-missing-violations
  "For `:filing/submit`, the general Act No. 34/1991 EEA-vs-non-EEA
  gate: when the engagement's own `:sector` restriction record
  (`marketentry.facts/sector-basis`) exempts EEA-established operators
  (energy/aviation/real-estate -- NOT fisheries, handled separately
  below) and the engagement's own `:eea-established?` is false,
  INDEPENDENTLY verify `:nonresident-clearance-obtained?`. The
  requirement itself is computed from the sector table, not merely
  trusted off an engagement-declared flag -- an engagement cannot
  simply decline to declare the requirement away."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)
          sb (facts/sector-basis (:sector e))]
      (when (and sb (:eea-exempt? sb)
                 (not (:eea-established? e))
                 (not (true? (:nonresident-clearance-obtained? e))))
        [{:rule :nonresident-clearance-missing
          :detail (str subject " (sector=" (:sector e) ") はEEA非設立の非居住投資家として"
                       "Act No. 34/1991のクリアランスを要するが未取得 -- 提出提案は進められない")}]))))

(defn- fisheries-cap-exceeded-violations
  "For `:filing/submit`, when the engagement's own `:sector` is
  `:fisheries` -- the ONE sector where EEA status does NOT waive the
  ownership cap -- INDEPENDENTLY recompute the applicable cap (25%,
  extendable to 33% only when the engagement's own
  `:extended-conditions-met?` is true) and HARD-hold if
  `:foreign-ownership-pct` exceeds it. REGARDLESS of
  `:eea-established?` -- never overridable by EEA status, unlike
  `nonresident-clearance-missing-violations` above. Structurally
  cannot fire for a non-fisheries engagement (gated on `:sector`)."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)]
      (when (= (:sector e) :fisheries)
        (let [cap (facts/fisheries-cap-pct (:extended-conditions-met? e))]
          (when (> (:foreign-ownership-pct e 0) cap)
            [{:rule :fisheries-cap-exceeded
              :detail (str subject " の外国資本比率(" (:foreign-ownership-pct e) "%)が水産業上限("
                          cap "%)を超過 -- EEA設立(" (:eea-established? e) ")であってもこの上限は免除されない")}]))))))

(defn- engagement-fee-mismatch-violations
  "For `:filing/submit`, INDEPENDENTLY recompute whether the
  engagement's own claimed fee equals base + months x rate."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)]
      (when-not (registry/engagement-fee-matches-claim? e)
        [{:rule :engagement-fee-mismatch
          :detail (str subject " の申告手数料(" (:claimed-fee e)
                      ")が独立再計算値(" (registry/compute-engagement-fee e) ")と一致しない")}]))))

(defn- already-drafted-violations
  "For `:filing/draft`, refuses to draft the SAME engagement twice."
  [{:keys [op subject]} st]
  (when (= op :filing/draft)
    (when (store/engagement-already-drafted? st subject)
      [{:rule :already-drafted
        :detail (str subject " は既にドラフト済み")}])))

(defn- already-submitted-violations
  "For `:filing/submit`, refuses to submit the SAME engagement twice."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (when (store/engagement-already-submitted? st subject)
      [{:rule :already-submitted
        :detail (str subject " は既に提出済み")}])))

(defn check
  "Censors a MarketEntry-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (stale-procurement-authority-violations request proposal)
                           (vsk-unregistered-violations request st)
                           (vat-agent-missing-violations request st)
                           (nonresident-clearance-missing-violations request st)
                           (fisheries-cap-exceeded-violations request st)
                           (engagement-fee-mismatch-violations request st)
                           (already-drafted-violations request st)
                           (already-submitted-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
