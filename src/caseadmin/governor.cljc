(ns caseadmin.governor
  "CaseAdminGovernor — the independent safety/traceability layer named
  in this repository's README/business-model.md, gating every
  documentation/logistics-coordination operation an advisor may
  propose. The governor never dispatches hardware itself and NEVER
  lets a proposal exercise, simulate exercising, or propose exercising
  ANY arrest, use-of-force, search/seizure-authorization,
  formal-charging or suspect-guilt/culpability-determination authority
  — every one of those is permanently out of scope for this actor, not
  merely gated behind escalation. This mirrors the Wave4
  person-facing-service safety guardrail (ADR-2607152500): decisions
  directly touching a person's liberty/due-process rights always
  exclude the closed op allowlist and always escalate. Modeled on
  cloud-itonami-isco-3353's socialbenefits.governor, with the same
  closed proposal-op allowlist + content-based scope-exclusion shape,
  adapted to this vertical's arrest/search/charge/guilt guardrail.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. officer provenance      — the proposing officer record must be
                                independently registered AND verified
                                before ANY proposal can commit or
                                escalate. Never trusts the proposal's
                                own claim of who the officer is.
    2. no-actuation             — proposal :effect must be :propose
                                (the governor never dispatches hardware
                                and never itself performs an
                                investigative or enforcement action; it
                                only gates what the advisor may
                                commit).
    3. closed op allowlist      — the proposal's :op must be one of the
                                four ops this actor is scoped to
                                (`closed-op-allowlist` below). This is
                                the STRUCTURAL guarantee: no op that
                                resembles making an arrest, authorizing
                                a search/seizure, filing a formal
                                charge or determining a suspect's guilt
                                or culpability exists anywhere in this
                                allowlist — such a proposal cannot even
                                reach a check, let alone pass one. Any
                                :op outside the allowlist is a HARD,
                                PERMANENT block.
    4. case-file basis          — a proposal for `:log-case-file-record`,
                                `:schedule-interview-appointment` or
                                `:coordinate-supply-order` must cite a
                                REGISTERED AND VERIFIED case belonging
                                to the officer's own precinct
                                (`:unknown-case` / `:case-unverified` /
                                `:case-wrong-precinct`).
                                `:flag-investigation-review` does NOT
                                require an existing case (it is the
                                channel by which a brand-new case is
                                surfaced for human intake).
    5. evidentiary-interpretation forbidden — `:log-case-file-record`
                                is a physical chain-of-custody /
                                metadata record ONLY (item, timestamp,
                                custodian chain). Any proposal carrying
                                an evidentiary-conclusion field
                                (`log-record-forbidden-keys` below —
                                e.g. `:conclusion`, `:finding`,
                                `:guilt`) is a HARD, PERMANENT block —
                                this actor never records an
                                interpretation of evidence, only its
                                physical handling.
    6. interview-substance forbidden — `:schedule-interview-appointment`
                                is scheduling logistics ONLY. Any
                                proposal carrying interview-content
                                fields (`interview-forbidden-keys`
                                below — e.g. `:summary`, `:statement`,
                                `:testimony`, `:confession`) is a HARD,
                                PERMANENT block — this actor never
                                conducts or summarizes an interview.
    7. scope exclusion           — independent, DEFENSE-IN-DEPTH layer
                                on top of #3/#5/#6: even for an
                                otherwise-allowed op, any proposal whose
                                free text (`:rationale` or `:note`)
                                names a finalization/execution ACTION
                                for an arrest, search/seizure
                                authorization, formal charge or
                                guilt/culpability determination
                                (`scope-excluded-terms` below) is a
                                HARD, PERMANENT block, evaluated
                                unconditionally on content. This actor
                                never exercises investigative-conclusion
                                or enforcement authority — it only
                                documents case files and coordinates
                                officer logistics.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off — these
  are :high/:safety-critical regardless of confidence):
    8. :op :flag-investigation-review (surfacing a case file that
                                needs human detective/inspector review
                                — new case, evidence-intake trigger,
                                deadline — ALWAYS requires human
                                review; it is never auto-resolved and
                                never appears in any phase's
                                auto-commit set; this is the ONLY path
                                by which an investigative observation
                                may be surfaced, and the robot's role
                                ends at \"here is an organized case
                                file\" — never \"here is what I think
                                happened\" or \"here is who did it\").
    9. an above-threshold :coordinate-supply-order (forensic/office
                                equipment procurement above
                                `supply-cost-escalation-threshold`
                                always needs human sign-off, regardless
                                of confidence — this is an escalation,
                                NOT a hard block, since an over-budget
                                supply request is not itself unsafe).
    10. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [caseadmin.store :as store]))

(def confidence-floor 0.6)

;; Forensic/office-equipment supply orders at or below this estimated
;; cost may be auto-commit-eligible (subject to confidence); above it,
;; ALWAYS escalates to a human regardless of confidence.
(def supply-cost-escalation-threshold 500)

;; The closed proposal-op allowlist. This governor NEVER allows any op
;; outside this set to commit or even escalate — an op outside this
;; set is a HARD, permanent block (see `hard-violations`
;; :op-not-allowed below), not merely un-auto-committable. This is a
;; case-file documentation/administrative-logistics coordination robot
;; ONLY: it has NO op, anywhere in this allowlist, that resembles
;; making an arrest, authorizing a search/seizure, filing a formal
;; charge, or determining a suspect's guilt or culpability. Those
;; capabilities are structurally absent, not gated.
(def closed-op-allowlist
  #{:log-case-file-record :schedule-interview-appointment
    :flag-investigation-review :coordinate-supply-order})

;; :flag-investigation-review always escalates to a human — never
;; auto-commit-eligible at any phase. It is the ONLY channel through
;; which an investigative observation may be surfaced.
(def ^:private always-escalate-ops #{:flag-investigation-review})

;; Ops that outside observers might expect a "police actor" to have —
;; named here explicitly (in addition to the closed-allowlist check
;; above) so the exclusion reads as an intentional, documented scope
;; boundary rather than an incidental unknown op. None of these are
;; ever defined as a real op anywhere in this codebase; they exist
;; ONLY as negative-test fixtures proving `closed-op-allowlist`
;; rejects them.
(def scope-excluded-ops
  #{:make-arrest :authorize-arrest :authorize-search :authorize-seizure
    :conduct-search :execute-search-warrant :file-formal-charge
    :file-charges :determine-guilt :determine-culpability
    :confirm-suspect-guilty})

;; log-case-file-record is a physical chain-of-custody / metadata
;; record ONLY. A proposal carrying any of these keys is smuggling an
;; evidentiary interpretation or conclusion into what must remain a
;; pure handling log.
(def log-record-forbidden-keys
  #{:conclusion :finding :guilt :culpability :determination
    :suspect-identified :evidentiary-interpretation :suspect-guilty?})

;; schedule-interview-appointment is scheduling logistics ONLY. A
;; proposal carrying any of these keys is smuggling interview
;; substance/content into what must remain pure appointment logistics.
(def interview-forbidden-keys
  #{:summary :statement :testimony :confession :interview-notes
    :interview-summary :interviewee-statement})

;; Scope-exclusion terms, phrased as the FINALIZATION/EXECUTION ACTION
;; (never a bare noun like "arrest", "guilt" or "charge" alone) — a
;; known self-tripping bug class in this fleet: a bare-noun term list
;; can accidentally match inside the mock advisor's own default
;; rationale text for a legitimate, allowed proposal, causing the
;; actor to self-block on its own happy path. This advisor's default
;; rationale template is "documented <op> for case <id>", which never
;; contains any of these full action phrases. See
;; `caseadmin.governor-test/
;; default-mock-advisor-proposals-never-self-trip-scope-exclusion`.
(def scope-excluded-terms
  ["make the arrest" "made the arrest" "execute the arrest" "executed the arrest"
   "authorize the arrest" "authorized the arrest" "issue the arrest warrant"
   "issued the arrest warrant"
   "authorize the search" "authorized the search" "authorize the seizure"
   "authorized the seizure" "conduct the search" "conducted the search"
   "execute the search warrant" "executed the search warrant"
   "file the formal charge" "filed the formal charge" "file formal charges"
   "filed formal charges" "file the charge" "filed the charge"
   "determine the suspect's guilt" "determined the suspect's guilt"
   "determine guilt" "determined guilt" "determine culpability"
   "determined culpability" "confirm the suspect's guilt"
   "confirmed the suspect's guilt" "establish the suspect's guilt"
   "established the suspect's guilt"
   "逮捕した" "逮捕を執行した" "逮捕状を発行した" "捜索を許可した" "差押えを許可した"
   "起訴した" "正式に起訴した" "有罪と断定した" "容疑者の有罪を確定した" "有罪を確定した"])

(defn out-of-scope?
  "True if any free-text field on `proposal` (:rationale or :note)
  contains a scope-excluded finalization/execution phrase for an
  arrest, search/seizure authorization, formal charge, or
  guilt/culpability determination."
  [proposal]
  (let [text (str (:rationale proposal) " " (:note proposal))]
    (boolean (some #(str/includes? text %) scope-excluded-terms))))

(defn- forbidden-keys-present [proposal forbidden-keys]
  (seq (filter #(contains? proposal %) forbidden-keys)))

(def ^:private case-required-ops
  #{:log-case-file-record :schedule-interview-appointment :coordinate-supply-order})

(defn- hard-violations [{:keys [proposal]} officer-record case-record]
  (let [{:keys [op case-id]} proposal
        needs-case? (contains? case-required-ops op)]
    (cond-> []
      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は捜査/執行行為を直接実行しない）"})

      (not (contains? closed-op-allowlist op))
      (conj {:rule :op-not-allowed
             :detail "closed allowlist 外の op（逮捕・捜索/差押えの許可・起訴・有罪判定の直接実行を含む一切の確定は許可されない）"})

      (nil? officer-record)
      (conj {:rule :unknown-officer :detail "未登録 officer への提案は不可"})

      (and officer-record (not (:verified? officer-record)))
      (conj {:rule :officer-unverified :detail "未検証 officer への提案は不可（登録のみでは不十分）"})

      (and needs-case? (nil? case-id))
      (conj {:rule :missing-case-id :detail "この op には case-id が必須"})

      (and needs-case? case-id (nil? case-record))
      (conj {:rule :unknown-case :detail "未登録 case への提案は不可"})

      (and needs-case? case-record (not (:verified? case-record)))
      (conj {:rule :case-unverified :detail "未検証 case への提案は不可（登録のみでは不十分）"})

      (and needs-case? case-record officer-record
           (not= (:precinct-id case-record) (:precinct-id officer-record)))
      (conj {:rule :case-wrong-precinct :detail "case が officer と別 precinct のもの"})

      (and (= :log-case-file-record op) (seq (forbidden-keys-present proposal log-record-forbidden-keys)))
      (conj {:rule :evidentiary-interpretation-forbidden
             :detail "log-case-file-record は物理的な chain-of-custody 記録のみ — 証拠解釈・結論は永久に禁止"})

      (and (= :schedule-interview-appointment op) (seq (forbidden-keys-present proposal interview-forbidden-keys)))
      (conj {:rule :interview-substance-forbidden
             :detail "schedule-interview-appointment は日程調整のみ — 供述内容の記録は永久に禁止"})

      (out-of-scope? proposal)
      (conj {:rule :scope-excluded
             :detail "逮捕・捜索/差押えの許可・起訴・有罪/責任判定を直接確定する提案は恒久的に許可されない（このactorは文書化とロジスティクス調整のみを行う）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `caseadmin.store/Store`. Pure — never mutates
  the store, never makes an arrest, never authorizes a search/seizure,
  never files a charge, never determines guilt or culpability."
  [_request _context proposal store]
  (let [officer-record (some->> (:officer-id proposal) (store/officer store))
        case-record (some->> (:case-id proposal) (store/case-file store))
        hard (hard-violations {:proposal proposal} officer-record case-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))
        over-threshold-supply-order?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-escalation-threshold))]
    {:ok? (and (not hard?) (not low?) (not always-risky?) (not over-threshold-supply-order?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky? over-threshold-supply-order?))}))
