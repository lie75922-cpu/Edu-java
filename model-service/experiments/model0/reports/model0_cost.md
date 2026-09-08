# MODEL-0 cost and runtime evidence

## Measured runs

| Model | Device | Train wall time | Valid inference | Test inference | Peak process RSS | Peak GPU | Checkpoint |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| NCDM | cpu | 5.590 s | 0.000881 ms/row | 0.001222 ms/row | 363,937,792 bytes (347.08 MiB) | none | 1,767,380 bytes (1.69 MiB) |
| ORCDF-NCD | cpu | 18.843 s | 0.001658 ms/row | 0.001235 ms/row | 1,227,460,608 bytes (1170.60 MiB) | none | 1,452,602 bytes (1.39 MiB) |

## Incremental ORCDF cost versus NCDM

- Training wall time: 13.254 s.
- Peak process RSS: 863,522,816 bytes (823.52 MiB).
- Checkpoint size delta: -314,778 bytes (-0.30 MiB).
- Base graph construction: 0.810 s.
- Total flipped graph construction during training:
  8.597 s.

## Runtime limitations

- Both measured runs use Python 3.12.14 and
  PyTorch 2.3.1+cpu on CPU. `GPU=none` describes the
  actual model runtime; it does not claim that no graphics hardware exists.
- The metric is sampled process RSS at each training/evaluation step and GPU
  allocator peak where available. No unmeasured GPU or GEAR-CD cost is inferred.
- GEAR-CD has no executable local model process, so its training time, peak RAM,
  peak GPU, and checkpoint size are correctly `NOT_AVAILABLE`, not zero.

