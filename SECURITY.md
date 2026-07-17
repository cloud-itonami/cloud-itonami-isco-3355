# Security Policy

This project handles police inspectors and detectives operating workflows,
directly touching due-process rights and, indirectly, a person's liberty and
physical safety. Treat vulnerabilities as potentially high impact even when
the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real case, evidence, victim, witness, suspect, officer or operator data
  exposure
- authorization bypass
- CaseAdmin Governor bypass
- any path by which the actor could make an arrest, authorize a search or
  seizure, file a formal charge, or determine a suspect's guilt or
  culpability
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on case data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real case/evidence/officer/operator data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
