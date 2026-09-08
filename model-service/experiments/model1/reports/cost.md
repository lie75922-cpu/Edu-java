# MODEL-1 Cost

| Candidate | Device | Train time | Validation inference | Final-test inference | Peak process RAM | Peak GPU | Checkpoint |
| --- | --- | ---: | ---: | ---: | --- | --- | --- |
| C40_TOPIC / NCDM | cpu | 25.350 s | 0.000826 ms/row | 0.000961 ms/row | 477,122,560 bytes (455.02 MiB) | none | 967,322 bytes (0.92 MiB) |
| C_FINE_EXERCISE / NCDM | cpu | 62.441 s | 0.001070 ms/row | 0.001048 ms/row | 477,122,560 bytes (455.02 MiB) | none | 15,955,246 bytes (15.22 MiB) |

`GPU=none` means no CUDA model runtime was used. Peak RAM is sampled process RSS. Checkpoints, local numeric derivatives, runtime ledgers, logs, virtual environments, raw data, and secrets remain ignored and are not uploaded.
