(ns culture.facts
  "Country-level regional-culture catalog for Iceland (ISL) -- national
  dishes, protected products, beverages, crafts, festivals and heritage
  sites, per ADR-2607171400 addendum 2 (cloud-itonami-municipality-
  culture-catalog Wave 1, in com-junkawasaki/root). Sibling namespace to
  the `marketentry.facts` / `statute.facts` catalogs of the iso3166
  siblings (ADR-2607141700); city-level counterparts live in the
  cloud-itonami-municipality-* repos.

  Catalog is keyed by UPPERCASE ISO3 (mirrors `statute.facts`); entries
  carry no :culture/municipality (that attribute is city-level only).

  Every entry cites a source URL that was actually fetched and read on
  :culture/retrieved-at -- never fabricated. Summaries state only what the
  cited source confirms. An item not in this table has NO spec-basis, full
  stop; extend `catalog`, do not invent an id/url.")

(def catalog
  "iso3 -> vector of culture entries."
  {"ISL"
   [{:culture/id "isl.dish.hakarl"
     :culture/name "Hákarl"
     :culture/country "ISL"
     :culture/kind :dish
     :culture/summary "National dish of Iceland consisting of Greenland shark or other sleeper shark cured with a particular fermentation process and hung to dry for four to five months."
     :culture/url "https://en.wikipedia.org/wiki/H%C3%A1karl"
     :culture/url-provenance :wikipedia-en
     :culture/retrieved-at "2026-07-17"}
    {:culture/id "isl.dish.thorramatur"
     :culture/name "Þorramatur"
     :culture/country "ISL"
     :culture/kind :dish
     :culture/summary "Selection of traditional Icelandic foods, mostly cooked meat and fish products, served during the midwinter season between January and March."
     :culture/url "https://en.wikipedia.org/wiki/%C3%9Eorramatur"
     :culture/url-provenance :wikipedia-en
     :culture/retrieved-at "2026-07-17"}
    {:culture/id "isl.dish.rugbraud"
     :culture/name "Rúgbrauð"
     :culture/country "ISL"
     :culture/kind :dish
     :culture/summary "Icelandic dark, dense and sweet rye bread, traditionally baked in a pot or steamed in wooden casks buried near geysers."
     :culture/url "https://en.wikipedia.org/wiki/R%C3%BAgbrau%C3%B0"
     :culture/url-provenance :wikipedia-en
     :culture/retrieved-at "2026-07-17"}
    {:culture/id "isl.product.skyr"
     :culture/name "Skyr"
     :culture/country "ISL"
     :culture/kind :product
     :culture/summary "Traditional Icelandic cultured dairy product with the consistency of strained yogurt and a milder flavor, consumed as a high-protein, low-fat food."
     :culture/url "https://en.wikipedia.org/wiki/Skyr"
     :culture/url-provenance :wikipedia-en
     :culture/retrieved-at "2026-07-17"}
    {:culture/id "isl.beverage.brennivin"
     :culture/name "Brennivín"
     :culture/country "ISL"
     :culture/kind :beverage
     :culture/summary "Icelandic distilled beverage flavored with caraway, considered the country's signature distilled beverage."
     :culture/url "https://en.wikipedia.org/wiki/Brenniv%C3%ADn"
     :culture/url-provenance :wikipedia-en
     :culture/retrieved-at "2026-07-17"}
    {:culture/id "isl.craft.lopapeysa"
     :culture/name "Lopapeysa"
     :culture/country "ISL"
     :culture/kind :craft
     :culture/summary "Icelandic style of sweater originating in the early or mid-20th century, with a distinctive yoke design, traditionally knitted from Icelandic lopi wool."
     :culture/url "https://en.wikipedia.org/wiki/Lopapeysa"
     :culture/url-provenance :wikipedia-en
     :culture/retrieved-at "2026-07-17"}
    {:culture/id "isl.festival.thorrablot"
     :culture/name "Þorrablót"
     :culture/country "ISL"
     :culture/kind :festival
     :culture/summary "Icelandic midwinter celebration held from mid-January to mid-February with dinners, speeches and poetry, revived in its modern form during the 19th-century nationalist movement."
     :culture/url "https://en.wikipedia.org/wiki/%C3%9Eorrabl%C3%B3t"
     :culture/url-provenance :wikipedia-en
     :culture/retrieved-at "2026-07-17"}
    {:culture/id "isl.heritage.thingvellir"
     :culture/name "Þingvellir National Park"
     :culture/country "ISL"
     :culture/kind :heritage
     :culture/summary "Site of Iceland's ancient parliament from 930 to 1798, now a national park on the Mid-Atlantic Ridge; a UNESCO World Heritage Site designated in 2004."
     :culture/url "https://en.wikipedia.org/wiki/%C3%9Eingvellir"
     :culture/url-provenance :wikipedia-en
     :culture/retrieved-at "2026-07-17"}]})

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
      :note (str "cloud-itonami-iso3166-isl culture catalog "
                 "(ADR-2607171400 addendum 2, Wave 1): " (count (get catalog "ISL"))
                 " ISL entries, each with a fetched-and-read citation. "
                 "Extend `culture.facts/catalog`, never fabricate an id/url.")})))

(defn by-kind [iso3 kind]
  (filterv #(= (:culture/kind %) kind) (spec-basis iso3)))
