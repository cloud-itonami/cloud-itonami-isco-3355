(ns caseadmin.store
  "SSoT for the ISCO-08 3355 police inspectors and detectives case-file
  documentation / administrative-logistics coordination actor (itonami
  actor pattern, ADR-2607121000 / CLAUDE.md Actors section; README's
  'Robotics premise' — a case-file intake and logistics robot performs
  evidence-log data entry, interview-appointment scheduling and
  forensic/office-supply coordination under this advisor/governor
  pair, which never dispatches hardware itself and NEVER exercises,
  simulates exercising, or proposes exercising ANY arrest, use-of-force,
  search/seizure-authorization, formal-charging or
  suspect-guilt/culpability-determination authority — every one of
  those capabilities is a permanently out-of-scope, structurally
  absent op; this actor cannot make an arrest, authorize a search,
  file a charge or determine guilt, no matter how confident the
  advisor is or how a human resumes an interrupted run). Modeled on
  cloud-itonami-isco-3353's socialbenefits.store (closed op allowlist +
  independently-registered-AND-verified provenance for both the
  proposing officer and the referenced case), itself modeled on
  cloud-itonami-isco-3313's accountingsupport.store.

  Domain:

    officer   — a registered detective/inspector {:officer-id :name
                :precinct-id :verified? boolean}. Independently
                registered/verified identity, never trusted from the
                proposal alone (\"officer/precinct record must be
                independently verified/registered before any
                action\"). This actor never determines this officer's
                investigative conclusions — it only logs, schedules
                and flags administrative/logistics records on the
                officer's behalf.
    case-file — a registered case {:case-id :precinct-id
                :max-supply-cost number :verified? boolean}.
                Independently registered/verified, never trusted from
                the proposal alone. `:max-supply-cost` is the
                registered per-case ceiling a proposed
                `:coordinate-supply-order` cost above which always
                escalates to a human — NOT a hard block, a supply
                order over budget just needs sign-off, it is not
                itself unsafe.
    record    — a committed operating record (evidence-log/
                chain-of-custody entry, interview-appointment
                scheduling proposal, investigation-review flag, or
                supply-order coordination proposal) — written ONLY via
                commit-record!. A committed record is NEVER an arrest,
                a search/seizure authorization, a formal charge, or a
                determination of a suspect's guilt or culpability —
                this actor documents and coordinates logistics, it
                never investigates or enforces.
    ledger    — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (officer [s officer-id])
  (case-file [s case-id])
  (records-of [s case-id])
  (ledger [s])
  (register-officer! [s o])
  (register-case! [s c])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (officer [_ officer-id] (get-in @a [:officers officer-id]))
  (case-file [_ case-id] (get-in @a [:cases case-id]))
  (records-of [_ case-id] (filter #(= case-id (:case-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-officer! [s o]
    (swap! a assoc-in [:officers (:officer-id o)] o) s)
  (register-case! [s c]
    (swap! a assoc-in [:cases (:case-id c)] c) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:officers {} :cases {} :records [] :ledger []}
                                   seed)))))
