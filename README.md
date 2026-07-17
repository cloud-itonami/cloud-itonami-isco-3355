# cloud-itonami-isco-3355

Open Occupation Blueprint for **ISCO-08 3355**: Police Inspectors and Detectives.

This repository designs a forkable OSS business for a police case-file
documentation and administrative-logistics coordination practice: a
case-file intake and logistics robot manages evidence-log entries,
interview-appointment scheduling and forensic/office-supply
coordination under a governor-gated actor — and structurally **never**
makes an arrest, authorizes a search or seizure, files a formal
charge, or determines a suspect's guilt or culpability itself.

## This actor has no arrest, search, charge, or guilt-determination authority

Police Inspectors and Detectives conduct criminal investigations, may
have arrest authority, and operate in a domain where wrong autonomous
action could cause someone's wrongful detention, physical harm, or
violation of due-process/civil rights. **This actor is a case-file
documentation/administrative-logistics coordination robot ONLY.** It
has NO op, anywhere in its allowlist, that resembles making an arrest,
authorizing a search or seizure, filing a formal charge, or
determining a suspect's guilt or culpability. These are **structurally
absent from the closed op-allowlist entirely**, not merely gated
behind escalation — under any circumstance, at any confidence level,
in any phase. Any observation the robot logs that suggests a case
needs human attention is surfaced ONLY via an always-escalating
`:flag-investigation-review` op that a human detective/inspector
reviews and acts on entirely themselves. This mirrors the Wave4
person-facing-service safety guardrail (ADR-2607152500): decisions
directly touching a person's liberty/due-process rights always exclude
the closed op allowlist and always escalate. The robot's role ends at
"here is an organized case file" — never "here is what I think
happened" or "here is who did it."

**Maturity: `:implemented`.** `src/caseadmin/` implements the
`CaseAdminActor` as a `langgraph.graph/state-graph`
(`caseadmin.actor`) wired to a `Case File Advisor`
(`caseadmin.advisor`) and an independent `CaseAdminGovernor`
(`caseadmin.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 44 tests / 144 assertions green (`clojure -M:test`).

HARD invariants (always hold, never overridable): officer provenance
(a proposal must resolve to an independently registered AND verified
officer record), a closed four-op proposal allowlist (any op outside
it — including anything that would make an arrest, authorize a
search/seizure, file a formal charge, or determine guilt or
culpability — is a permanent HARD block, because no such op exists in
the allowlist to begin with), no-actuation (`:effect` must be
`:propose`), a registered-and-verified case-file basis (for the three
ops that reference one), an evidentiary-interpretation-forbidden check
(`:log-case-file-record` may only carry physical
chain-of-custody/handling metadata, never a conclusion), an
interview-substance-forbidden check (`:schedule-interview-appointment`
may only carry scheduling logistics, never interview content), and a
content-based scope-exclusion check: any proposal whose free text
names a finalization/execution action for an arrest, search/seizure
authorization, formal charge, or guilt/culpability determination is a
permanent HARD block, independent of and in addition to the
op-allowlist check. This actor **never** exercises, simulates
exercising, or proposes exercising any arrest, use-of-force,
search/seizure-authorization, formal-charging, or
suspect-guilt/culpability-determination authority — it only documents
case files and coordinates officer logistics.

Always-escalate (human sign-off regardless of confidence, mapping this
repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-investigation-review` (surfacing a case file that needs human
detective/inspector review — new case, evidence-intake trigger,
deadline — always requires human review; never auto-resolved, never in
any phase's auto-commit set — this is the ONLY channel by which an
investigative observation may be surfaced) and any
`:coordinate-supply-order` above the registered per-case cost
threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical/administrative domain work**. Here a case-file
intake and logistics robot performs evidence-log data entry,
interview-appointment scheduling and forensic/office-supply
coordination under an actor that proposes actions and an independent
**CaseAdminGovernor** that gates them. The governor never dispatches
hardware itself; `:high`/`:safety-critical` actions (such as flagging
a case for investigation review, or an above-threshold supply order)
require human sign-off — and no action in this actor's closed op
allowlist can ever make an arrest, authorize a search/seizure, file a
formal charge, or determine a suspect's guilt or culpability.

## Core Contract

```text
officer intake queue + case-file directory + supply policy
        |
        v
Case File Advisor -> CaseAdminGovernor -> log record/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
make an arrest, authorize a search or seizure, file a formal charge,
determine guilt or culpability, suppress an operating record, or
disclose sensitive data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3355`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
