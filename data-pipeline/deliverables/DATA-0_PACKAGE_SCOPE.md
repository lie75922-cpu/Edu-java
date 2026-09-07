# DATA-0 Delivery Package Scope

`DATA-0-20260907-delivery.zip` is a review package, not a raw-data distribution.

Included:

- DATA-0 parser, audit, canonical-schema, and reproducible-sampling source;
- DATA-0 tests and package metadata;
- all committed DATA-0 reports, including saved audit samples;
- the canonical manifest and schema;
- DATA-0 implementation guidance and CI workflow context.

Excluded:

- Junyi raw archives, CSV files, and locally materialised candidate data;
- `.data` work directories, temporary virtual environments, caches, and build outputs;
- `.env`-like files, local service credentials, and dependency directories.

The package preserves the `CONDITIONAL_GO` Final Gate and stops before V0.2. It
does not claim direct acquisition of the primary data source, does not turn raw
prerequisite strings or annotation scores into an approved production graph,
and does not supply a measured peak-RAM value for the completed full audit.
