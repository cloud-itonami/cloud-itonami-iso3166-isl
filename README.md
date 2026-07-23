# cloud-itonami-iso3166-isl

Open ISO 3166 Blueprint for **ISL**: Iceland. **`:implemented`** --
`src/marketentry/*` is a running langgraph-clj StateGraph actor: a
MarketEntry-LLM advisor sealed behind an independent Market-Entry
Compliance Governor. Flagship check `stale-procurement-authority`
(catches an LLM citing the decommissioned Ríkiskaup instead of the
current Fjársýslan/FMA); domain checks `vsk-unregistered`,
`vat-agent-missing`, `nonresident-clearance-missing`,
`fisheries-cap-exceeded`.

```
clojure -M:dev:test
```

This repository designs a forkable OSS business for an independent
public-sector market-entry consultant: an already-incorporated operator
(e.g. a `cloud-itonami-cofog-{code}`, `cloud-itonami-isco-{code}`,
`cloud-itonami-unspsc-{segment}` or `cloud-itonami-{ISIC}` blueprint
fork) gets a Compliance Advisor + independent **Market-Entry Compliance
Governor** to navigate public-procurement registration, local business/
tax registration, and EEA single-market rules in Iceland, so the
operator can win and service a government contract without hiring a
full in-house compliance department.

## No robotics premise — digital/data service exemption

Market-entry and procurement-compliance navigation is a pure data/software
service with no physical-domain work (portal registration, document
checklists, regulatory-change monitoring) — the same exemption class as
`cloud-itonami-6310` (HR SaaS replacement) and `cloud-itonami-gtin-*`.
`blueprint.edn` sets `:itonami.blueprint/robotics false` and
`:required-technologies` lists only real capabilities (`:identity`,
`:forms`, `:dmn`, `:bpmn`, `:audit-ledger`), no `:robotics`.

## Core Contract

```text
operator intake + prior filing history
        |
        v
Compliance Advisor -> Market-Entry Compliance Governor -> filing draft, or human sign-off
        |
        v
gated portal registration / filing submission + audit ledger
```

No automated proposal can submit a portal registration or filing the
governor refuses, suppress a compliance record, or claim a legal/tax
conclusion the governor has not cleared. `:filing/submit` is never in any
phase's `:auto` set — it always requires human sign-off (mirrors
`cloud-itonami-M6910`'s `filing-submit-never-auto-at-any-phase`
invariant).

## Governor checks

`marketentry.governor/check` runs every proposal through these HARD
checks (a human approver cannot override a HARD violation) before the
confidence-floor / actuation gate. Each check's regulatory fact is
`marketentry.facts`-cited; source URLs below were fetched and verified,
not invented:

| Check | Fires on | Source |
|---|---|---|
| `no-spec-basis` | any `jurisdiction/assess`\|`filing/draft`\|`filing/submit` proposal that doesn't cite an official source | n/a (structural) |
| `evidence-incomplete` | `filing/draft`\|`filing/submit` before the jurisdiction's full evidence checklist (Fyrirtækjaskrá registration, VSK/VAT registration, Fjársýslan procurement registration, agent record) is on file | https://www.skatturinn.is/english/company-registration/register-a-company/private-limited-companies/ |
| `stale-procurement-authority` **(flagship)** | a proposal citing **Ríkiskaup** as the procurement authority — Ríkiskaup was formally decommissioned 2024-08-01, all responsibilities transferred to **Fjársýslan (FMA)** | https://island.is/en/news/the-financial-management-authority-fma-has-assumed-all-responsibilities-of |
| `vsk-unregistered` | `filing/submit` where `:requires-vsk-registration?` is true but `:vsk-registered?` is false — Iceland has **no VAT registration threshold**; foreign entities must register from the first taxable supply, via form RSK 5.02 | https://www.skatturinn.is/english/companies/value-added-tax/ |
| `vat-agent-missing` | `filing/submit` where `:requires-vat-agent?` is true but `:has-vat-agent?` is false — a foreign company without a permanent establishment must appoint a locally domiciled agent for VAT notification/collection/remittance | https://www.skatturinn.is/english/companies/value-added-tax/ |
| `nonresident-clearance-missing` | `filing/submit` in a sector (energy, aviation, real estate) where Act No. 34/1991 exempts EEA-established operators, but the engagement is non-EEA-established and lacks clearance | Act on Investment by Non-Residents in Business Enterprises, No. 34/1991 (amended by Act No. 121/1993, Act No. 46/1996); general register: https://www.althingi.is/lagas/nuna/ |
| `fisheries-cap-exceeded` | `filing/submit` in the **fisheries** sector, foreign ownership above 25% (extendable to 33% only under statutory conditions) — **the one sector where EEA-established status does NOT waive the cap** | Act No. 34/1991 (as amended) |
| `engagement-fee-mismatch` | `filing/submit` where `:claimed-fee` ≠ independently recomputed `base-fee + monthly-rate x monitoring-months` | n/a (arithmetic) |
| `already-drafted` / `already-submitted` | a second `filing/draft`/`filing/submit` for the same engagement | n/a (structural, off dedicated `:drafted?`/`:submitted?` facts) |

Public procurement itself is governed by the Public Procurement Act,
Law No. 120/2016 (Lög nr. 120/2016 um opinber innkaup), which
implements EU Directive 2014/24/EU as transposed via the EEA Agreement
— Iceland is an EEA/EFTA member, not an EU member state.
Source: https://www.althingi.is/lagas/nuna/2016120.html

## Actuation

`:filing/draft` and `:filing/submit` are the two real-world acts this
actor performs (preparing a portal registration package, and actually
submitting one). Two independent layers agree neither ever
auto-commits:

- **`marketentry.phase`**: `:filing/draft`/`:filing/submit` are
  permanently absent from every rollout phase's `:auto` set (phase
  0 through 3) — not a rollout milestone still to come, a structural
  fact.
- **`marketentry.governor`**: both ops carry
  `:stake :actuation/draft-filing`/`:actuation/submit-filing`, members
  of `governor/high-stakes`, which forces `:escalate?` even when the
  proposal is otherwise clean.

Every `filing/draft`/`filing/submit` therefore always reaches
`operation.cljc`'s `:request-approval` node
(`interrupt-before #{:request-approval}`) and pauses for a real human
market-entry operator's sign-off — see `test/marketentry/
governor_contract_test.clj`'s `draft-always-escalates-then-human-
decides` / `submit-always-escalates-then-human-decides`.

## What this is NOT

- **Not the government of Iceland.** See
  [`docs/business-model.md`](docs/business-model.md) for the boundary with
  `com-etzhayyim-ooyake` (read-only civic mirror), `matsurigoto` (sovereign
  statecraft), `com-etzhayyim-toritsugi` (individual citizen concierge),
  `legal-entity.etzhayyim.com` (read-only data aggregation), and
  `cloud-itonami-M6910` (company incorporation — a different regulatory
  phase this blueprint assumes is already complete).
- **Not legal or tax advice.** Every regulatory claim must cite the
  official source and route final filings to Icelandic-licensed counsel
  or a registered agent where the law requires licensed representation.

## Capability layer

Resolves via [`kotoba-lang/iso3166`](https://github.com/kotoba-lang/iso3166)
(ISO 3166 `ISL`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.

## Statute catalog

Alongside `marketentry.facts` (public-procurement market-entry only,
narrow scope), this repo carries a **general-law compliance catalog**
(ADR-2607141700, `cloud-itonami-compliance-fact-federation`) — statutes
a company generally must track for compliance, orthogonal to the
procurement-specific facts above:

| Topic | Law | Source |
|---|---|---|
| corporate-governance / incorporation | Lög um einkahlutafélög (Act on Private Limited Companies), Law No. 138/1994 | https://www.althingi.is/lagas/nuna/1994138.html |
| data-protection / privacy | Lög um persónuvernd og vinnslu persónuupplýsinga (Data Protection Act, implements EU Regulation 2016/679 GDPR), Law No. 90/2018 | https://www.althingi.is/lagas/nuna/2018090.html |
| labor / employment | Lög um aðbúnað, hollustuhætti og öryggi á vinnustöðum (Act on Working Environment, Health and Safety in Workplaces), Law No. 46/1980 | https://www.althingi.is/lagas/nuna/1980046.html |

- `src/statute/facts.cljc` — the catalog, source of truth.
- `schema/statute.edn` — DataScript schema.
- `data/datascript-tx.edn` — derived DataScript tx-data (regenerated
  from the catalog, never hand-edited).

This catalog does **not** repeat or contradict `marketentry.facts`'s
procurement-authority currency trap (Fjársýslan/FMA is current, NOT the
decommissioned Ríkiskaup) — that fact belongs to the public-procurement
domain `marketentry.facts` already owns; nothing here cites Ríkiskaup.
Same provenance discipline as every catalog in this repo: every entry
cites an official source that was actually fetched and read, never
invented. An item not in `statute.facts/catalog` has no spec-basis —
extend the catalog, never fabricate an id/url.

## Culture catalog

This repo carries a **country-level regional-culture catalog**
(ADR-2607171400 addendum 2, `cloud-itonami-municipality-culture-catalog`
Wave 1, in `com-junkawasaki/root`) — national dishes, protected products,
beverages, crafts, festivals and heritage sites for Iceland:

- `src/culture/facts.cljc` — the catalog, source of truth (keyed by
  uppercase ISO3, mirroring the `statute.facts` convention above).
- `schema/culture.edn` — DataScript schema.
- `data/culture-tx.edn` — derived DataScript tx-data (regenerated from
  the catalog, never hand-edited).

City-level counterparts live in the `cloud-itonami-municipality-*` repos.
Same provenance discipline as the compliance catalogs: every entry cites a
source URL that was actually fetched and read on `:culture/retrieved-at`;
summaries state only what the cited source confirms. An item not in
`culture.facts/catalog` has no spec-basis — never fabricate one.
