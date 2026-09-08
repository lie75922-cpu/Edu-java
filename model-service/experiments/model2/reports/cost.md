# MODEL-2 Cost and Runtime

| Phase | Device | Wall time | Peak process RAM | GPU |
| --- | --- | ---: | --- | --- |
| C40 inference-only diagnostics | cpu | 10.953 s | 401,506,304 bytes (382.91 MiB) | none |
| Rasch / IRT-1PL | cpu | 4.179 s | 409,608,192 bytes (390.63 MiB) | none |
| Total controlled diagnostic | cpu | 71.163 s | N/A | N/A |

Runtime versions: python=3.12.14, torch=2.3.1+cpu, numpy=1.26.4, scikit-learn=1.5.2, psutil=6.1.1.
The C40 checkpoint and MODEL-1 derivatives were reused read-only. The Rasch
checkpoint, runtime ledger, and all numeric derivatives remain ignored.
