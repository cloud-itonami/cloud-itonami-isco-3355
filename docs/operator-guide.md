# Operator Guide

## First Deployment

1. Define the operator's precinct/unit scope and case-intake process.
2. Define consent and purpose categories for case-file data handling.
3. Run synthetic operating cases (no real case, evidence, victim, witness or
   suspect data in this repository).
4. Enable human-reviewed sign-off for `:high`/`:safety-critical` actions —
   including every `:flag-investigation-review` and every above-threshold
   `:coordinate-supply-order`.
5. Measure operating outcomes and audit coverage.

## Minimum Production Controls

- consent and disclosure log
- safety-critical escalation path
- provenance for all operating records
- human review for high-risk cases
- audit export for all gated actions

## No Arrest, Search, Charge, or Guilt-Determination Authority

This actor is a case-file documentation/administrative-logistics
coordination robot ONLY. Operators MUST NOT configure, extend or fork this
actor to add an op that makes an arrest, authorizes a search or seizure,
files a formal charge, or determines a suspect's guilt or culpability, or
that otherwise exercises any investigative-conclusion or enforcement
authority. Any such change removes the structural guarantee this repository
is built around and voids certification (see [`GOVERNANCE.md`](../GOVERNANCE.md)).
Every investigative observation must route through
`:flag-investigation-review` to a human detective/inspector — the robot's
role ends at "here is an organized case file," never "here is what I think
happened" or "here is who did it."

## Certification

Certified operators must prove that the governor gates every
safety-critical robot action, that safety-critical risks escalate to
humans, and that no build of this actor has ever added an op resembling an
arrest, a search/seizure authorization, a formal charge, or a
guilt/culpability determination to the closed allowlist.
