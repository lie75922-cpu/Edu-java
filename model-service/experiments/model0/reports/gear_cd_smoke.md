# MODEL-0 GEAR-CD smoke report

## Scope and result

**SMOKE_BLOCKED_NO_VERIFIED_RUNTIME_OR_COMMON_IDENTITY_MAPPING**. No GEAR-CD
model training, no full Medium run, and no model test evaluation occurred. This
is the required smoke-only route, not a production integration decision.

## Local reference inventory

| Check | Result |
| --- | --- |
| Reference archive present | True |
| Extracted reference data present | True |
| Local Python model source files | 0 |
| Local dependency declaration files | 0 |
| Reference train JSON records | 279086 |
| Reference test JSON records | 10000 |
| Reference directed graph numeric nodes | 776 |
| Reference directed graph unique edges | 981 |
| Reference undirected graph numeric nodes | 362 |
| Reference undirected graph unique edges | 1040 |

The local artifact contains an extracted data package, not a verified executable
GEAR-CD model runtime or dependency declaration. Its records use a separate
numeric-concept representation. No auditable mapping from those numeric concepts
or reference exercises to the frozen MODEL-0 identities was found or inferred.

## Frozen input adapter-contract check

| Frozen MODEL-0 property | Value |
| --- | ---: |
| Students | 10000 |
| Q-matrix Exercise rows | 624 |
| Topic columns | 40 |
| Student split | 7000 / 1000 / 2000 |

The current local GEAR artifact cannot consume this contract without both a
verifiable GEAR-CD runtime and an approved identity mapping. A tiny model smoke
was therefore correctly blocked before construction; a fabricated adapter or a
full Medium training run would violate Issue #4.

## Environment and cost evidence

- Runtime inspected: Python 3.12.14, PyTorch
  2.3.1+cpu.
- CUDA usable by this isolated runtime: False.
- Peak RAM/GPU and training time: **not available**, because no GEAR-CD model
  process was executable. They are not inferred from NCDM or ORCDF.

## Next approval boundary

Obtain a verified GEAR-CD code/runtime source, an auditable common identity
mapping, and Owner/architect approval before any full Medium GEAR-CD training.

