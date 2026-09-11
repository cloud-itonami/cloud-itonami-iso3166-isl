(ns marketentry.facts
  "Iceland (ISL) market-entry catalog. Every value here must trace to a
  source that was actually verified, never invented -- see
  `marketentry.governor`'s spec-basis check.

  Two currency traps this catalog exists to defend against (an LLM's
  training data is likely stale on both):

    1. `:owner-authority` is Fjársýslan (the Financial Management
       Authority, FMA) -- NOT Ríkiskaup. Ríkiskaup, the historical
       central procurement authority, was formally DECOMMISSIONED on
       2024-08-01; all responsibilities and staff transferred to
       Fjársýslan. `procurement-authority-deprecated` names the stale
       value so `marketentry.governor` can HARD-hold any proposal that
       still cites it.
       Source: https://island.is/en/news/the-financial-management-authority-fma-has-assumed-all-responsibilities-of
    2. Company registration is administered by Skatturinn (Iceland's
       tax authority) -- NOT the \"Directorate of Internal Revenue\",
       a legacy/superseded name.
       Source: https://www.skatturinn.is/english/company-registration/register-a-company/private-limited-companies/")

(def procurement-authority-current
  "Fjársýslan (Financial Management Authority, FMA) -- the CURRENT
  central procurement authority since Ríkiskaup was decommissioned
  2024-08-01. Source:
  https://island.is/en/news/the-financial-management-authority-fma-has-assumed-all-responsibilities-of"
  "Fjársýslan (FMA)")

(def procurement-authority-deprecated
  "Names an advisor proposal may NOT cite as the current procurement
  authority -- Ríkiskaup was decommissioned 2024-08-01 and its
  responsibilities transferred to Fjársýslan. Any proposal citing one
  of these is stale-training-data, not a live fact, and is a HARD
  governor violation regardless of how confident the proposal is."
  #{"Ríkiskaup" "Rikiskaup"})

(defn stale-procurement-authority?
  "True when `authority` names the decommissioned Ríkiskaup rather than
  the current Fjársýslan (FMA)."
  [authority]
  (contains? procurement-authority-deprecated authority))

(def catalog
  {"ISL" {:name "Iceland"
          :owner-authority procurement-authority-current
          :legal-basis "Public Procurement Act, Law No. 120/2016 (Lög nr. 120/2016 um opinber innkaup) -- implements EU Directive 2014/24/EU, transposed via the EEA Agreement (Iceland is an EEA/EFTA member, not an EU member state)"
          :national-spec "Fjársýslan (FMA) central procurement, per Law No. 120/2016"
          :provenance "https://www.althingi.is/lagas/nuna/2016120.html"
          :required-evidence ["Fyrirtækjaskrá (Company Register) registration record"
                              "VSK/VAT registration record (form RSK 5.02)"
                              "Fjársýslan (FMA) procurement registration record"
                              "Authorized-representative / local VAT agent record"]
          :rep-owner-authority "Skatturinn"
          :rep-legal-basis "Foreign companies without a permanent establishment in Iceland must appoint a locally domiciled agent for VAT notification, collection and remittance"
          :rep-provenance "https://www.skatturinn.is/english/companies/value-added-tax/"
          :corporate-number-owner-authority "Skatturinn / Fyrirtækjaskrá"
          :corporate-number-legal-basis "Fyrirtækjaskrá (Company Register) registration; VSK number via form RSK 5.02 -- no registration threshold, foreign entities must register from the FIRST taxable supply"
          :corporate-number-provenance "https://www.skatturinn.is/english/companies/value-added-tax/"}
   "USA" {:name "United States" :owner-authority "GSA/SAM.gov" :legal-basis "FAR" :national-spec "SAM.gov" :provenance "https://sam.gov/"
          :required-evidence ["EIN record" "SAM.gov registration record" "State business registration record" "SAM UEI verification record"]}
   "ZAF" {:name "South Africa" :owner-authority "CSD/eTender" :legal-basis "PFMA" :national-spec "CSD" :provenance "https://www.etenders.gov.za/"
          :required-evidence ["CSD registration" "CIPC record" "SARS tax clearance" "Authorized-representative record"]}
   "BRA" {:name "Brazil" :owner-authority "Compras.gov.br" :legal-basis "Lei 14.133/2021" :national-spec "Compras.gov.br" :provenance "https://www.gov.br/compras/"
          :required-evidence ["CNPJ record" "Compras.gov.br registration" "SICAF record" "Authorized-representative record"]}})

(defn spec-basis [iso3] (get catalog iso3))
(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s) missing (remove catalog iso3s)]
     {:requested (count iso3s) :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note "R0 catalog seed"})))
(defn required-evidence-satisfied? [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (= (count required-evidence) (count (filter (set submitted) required-evidence)))))
(defn evidence-checklist [iso3] (:required-evidence (spec-basis iso3) []))
(defn rep-spec-basis [iso3]
  (when-let [sb (spec-basis iso3)]
    (when (:rep-owner-authority sb)
      (select-keys sb [:rep-owner-authority :rep-legal-basis :rep-provenance]))))
(defn corporate-number-spec-basis [iso3]
  (when-let [sb (spec-basis iso3)]
    (when (:corporate-number-owner-authority sb)
      (select-keys sb [:corporate-number-owner-authority :corporate-number-legal-basis :corporate-number-provenance]))))

;; ----------------------- sector-differentiated foreign-investment gate -----------------------

(def sector-restrictions
  "Act on Investment by Non-Residents in Business Enterprises, No. 34/1991
  (as amended by Act No. 121/1993 and Act No. 46/1996) -- sector-scoped
  restrictions on foreign/non-resident ownership. Each sector's
  EEA-exemption status and any independent ownership cap are DIFFERENT
  from each other -- this table deliberately does NOT flatten them into
  one figure; `marketentry.governor` reads each sector's own record.
  General source (Icelandic legislation database):
  https://www.althingi.is/lagas/nuna/ (specific 1991/034 deep-link not
  independently verified; do not cite a more specific URL than this)."
  {:fisheries   {:cap-pct 25 :extended-cap-pct 33 :eea-exempt? false
                 :legal-basis "Act No. 34/1991 (as amended by Act No. 121/1993, Act No. 46/1996) -- fisheries is the ONE sector where EEA nationals are NOT exempted from the foreign-shareholding cap; 25% is extendable to 33% only under statutory conditions"}
   :energy      {:cap-pct nil :extended-cap-pct nil :eea-exempt? true
                 :legal-basis "Act No. 34/1991 -- only Icelandic persons may own waterfall/geothermal exploitation rights for non-domestic use; EEA-established companies are exempt from this restriction"}
   :aviation    {:cap-pct 49 :extended-cap-pct nil :eea-exempt? true
                 :legal-basis "Act No. 34/1991 -- combined non-resident ownership capped at 49%; EEA-domiciled individuals are exempt from the cap"}
   :real-estate {:cap-pct nil :extended-cap-pct nil :eea-exempt? true
                 :legal-basis "Act No. 34/1991 -- non-EEA/EFTA nationals generally need a Ministry permit to purchase land/real estate; EEA/EFTA nationals are exempt from the permit requirement"}})

(defn sector-basis
  "The Act No. 34/1991 restriction record for `sector`, or nil when the
  sector is unrestricted (the general default -- Act No. 34/1991 does
  not restrict foreign investment outside these four sectors)."
  [sector]
  (get sector-restrictions sector))

(defn sector-eea-exempt?
  "True when `sector` exempts EEA-established operators from its
  restriction (energy/aviation/real-estate). False for fisheries, the
  one sector where EEA status does NOT waive the limit. nil (falsy)
  when the sector carries no restriction at all."
  [sector]
  (boolean (:eea-exempt? (sector-basis sector))))

(defn fisheries-cap-pct
  "The applicable foreign-ownership cap for a fisheries engagement:
  the base 25% cap, or the 33% extension when the engagement's own
  `:extended-conditions-met?` ground truth is true."
  [extended-conditions-met?]
  (let [{:keys [cap-pct extended-cap-pct]} (sector-basis :fisheries)]
    (if extended-conditions-met? extended-cap-pct cap-pct)))
