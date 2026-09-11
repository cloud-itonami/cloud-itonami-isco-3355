# Contributing

`cloud-itonami-isco-3355` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
kbb -M:dev:test
kbb -M:lint
```

Keep changes small and include tests for policy, audit, store or disclosure
behavior.

## Rules

- Do not commit real case, evidence, victim, witness, suspect, officer or
  operator data, credentials or operating documents.
- Keep production writes and disclosures behind CaseAdmin Governor.
- **Never add an op that makes an arrest, authorizes a search or seizure,
  files a formal charge, or determines a suspect's guilt or culpability, or
  that otherwise exercises any investigative-conclusion or enforcement
  authority.** This actor's closed proposal-op allowlist is a hard scope
  boundary, not a starting point to extend. Any PR that proposes such an op
  will be rejected.
- Treat this occupation's workflows as high-risk: add tests for permission,
  purpose, safety and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
