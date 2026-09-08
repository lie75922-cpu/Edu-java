# MODEL-0R Cost

| Protocol | Model | Device | Train time | Validation / calibration | Test inference | Peak process RAM | Peak GPU | Checkpoint |
| --- | --- | --- | ---: | --- | ---: | --- | --- | --- |
| Warm | NCDM | cpu | 6.723 s | 0.000863 ms/row | 0.003424 ms/row | 1,157,201,920 bytes (1103.59 MiB) | none | 1,767,322 bytes (1.69 MiB) |
| Warm | ORCDF-NCD | cpu | 22.239 s | 0.001413 ms/row | 0.001397 ms/row | 1,160,790,016 bytes (1107.02 MiB) | none | 1,452,584 bytes (1.39 MiB) |
| Cold | NCDM global + k=5 representation-only adaptation | cpu | 393.561 s | 45.094 ms/student calibration mean | 0.040393 ms/row | 345,567,232 bytes (329.56 MiB) | none | 1,767,420 bytes (1.69 MiB) |

`GPU=none` means no CUDA model runtime was used.  Process RSS is sampled while
the process runs.  Large checkpoints, numeric derivatives, Torch runtime,
virtual environments, logs, and source data remain ignored and are not
uploaded.
