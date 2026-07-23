(ns statute.facts
  "General-law compliance catalog for Iceland (ISL) -- extends this repo's
  existing `marketentry.facts` (public-procurement market-entry only,
  narrow scope) with a second, orthogonal catalog of statutes a company
  generally must track for compliance. Mirrors cloud-itonami-iso3166-jpn/
  -usa/-esp/-swe/-nor/-dnk/-fin's `statute.facts` (ADR-2607141700,
  cloud-itonami-compliance-fact-federation).

  This catalog does NOT repeat `marketentry.facts`'s procurement-authority
  currency trap (Fjársýslan/FMA is the current central procurement
  authority -- NOT the decommissioned Ríkiskaup, superseded 2024-08-01) --
  that fact belongs to the public-procurement domain `marketentry.facts`
  already owns and cites. Nothing below cites Ríkiskaup as a current
  authority, and nothing below duplicates or contradicts a
  `marketentry.facts` citation; the two catalogs are orthogonal (general
  company/data/labor law here, procurement-specific law there).

  Every entry cites an OFFICIAL althingi.is (Alþingi, Iceland's
  Parliament -- the SAME authoritative legal-text portal
  `marketentry.facts` already cites for the Public Procurement Act,
  Law No. 120/2016, and Act No. 34/1991) URL -- never fabricated. A law
  not in this table has NO spec-basis, full stop; extend `catalog`, do
  not invent an id/url. Title, law number, and entry-into-force date for
  every entry below were independently WebFetch-verified against the
  live althingi.is page on 2026-07-23.")

(def catalog
  "iso3 -> vector of statute entries."
  {"ISL"
   [{:statute/id "isl.einkahlutafelagalog-1994"
     :statute/title "Lög um einkahlutafélög (Act on Private Limited Companies)"
     :statute/jurisdiction "ISL"
     :statute/kind :law
     :statute/law-number "Law No. 138/1994"
     :statute/url "https://www.althingi.is/lagas/nuna/1994138.html"
     :statute/url-provenance :official-althingi
     :statute/enacted-date "1995-01-01"
     :statute/retrieved-at "2026-07-23"
     :statute/topic #{:corporate-governance :incorporation}}
    {:statute/id "isl.personuverndarlog-2018"
     :statute/title "Lög um persónuvernd og vinnslu persónuupplýsinga (Data Protection Act -- implements EU Regulation 2016/679, GDPR)"
     :statute/jurisdiction "ISL"
     :statute/kind :law
     :statute/law-number "Law No. 90/2018"
     :statute/url "https://www.althingi.is/lagas/nuna/2018090.html"
     :statute/url-provenance :official-althingi
     :statute/enacted-date "2018-07-15"
     :statute/retrieved-at "2026-07-23"
     :statute/topic #{:data-protection :privacy}}
    {:statute/id "isl.vinnuverndarlog-1980"
     :statute/title "Lög um aðbúnað, hollustuhætti og öryggi á vinnustöðum (Act on Working Environment, Health and Safety in Workplaces)"
     :statute/jurisdiction "ISL"
     :statute/kind :law
     :statute/law-number "Law No. 46/1980"
     :statute/url "https://www.althingi.is/lagas/nuna/1980046.html"
     :statute/url-provenance :official-althingi
     :statute/enacted-date "1981-01-01"
     :statute/retrieved-at "2026-07-23"
     :statute/topic #{:labor :employment}}]})

(defn spec-basis [iso3] (get catalog iso3))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-iso3166-isl statute.facts Wave 0 (ADR-2607141700): "
                 (count (get catalog "ISL")) " ISL statutes seeded with an "
                 "official althingi.is citation. Extend "
                 "`statute.facts/catalog`, never fabricate a law-id or URL.")})))

(defn by-topic [iso3 topic]
  (filterv #(contains? (:statute/topic %) topic) (spec-basis iso3)))
