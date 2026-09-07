# DATA-0 Graph Audit

| Relation source | Nodes | Edges | Components | Largest component ratio | Self loops | Cyclic SCCs |
| --- | --- | --- | --- | --- | --- | --- |
| Metadata prerequisite analysis view | 833 | 979 | 69 | 0.890756 | 2 | 3 |
| Topic and Area hierarchy | 863 | 855 | 8 | 0.363847 | 0 | 0 |
| RCD K_Directed | 776 | 981 | 10 | 0.958763 | 2 | 3 |
| RCD K_Undirected | 362 | 1040 | 9 | 0.875691 | 1 | N/A |

## Annotation semantics

- relationship_annotation_training: NOT_DERIVED: scalar annotation scores have no audited operational edge direction or threshold.; rows=1131; dangling endpoint rows=0
- relationship_annotation_testing: NOT_DERIVED: scalar annotation scores have no audited operational edge direction or threshold.; rows=823; dangling endpoint rows=0

Metadata prerequisites, annotation scores, RCD directed pairs, RCD undirected pairs, and Topic/Area hierarchy remain separated. RCD numeric concept IDs are not mapped to current raw external IDs, so they are not merged.

Representative raw-prerequisite cyclic components: [{"node_count": 3, "nodes": ["adding_and_subtracting_radicals", "radical_multiplication_and_division", "simplifying_radicals"], "representative_cycle": ["adding_and_subtracting_radicals", "radical_multiplication_and_division", "simplifying_radicals", "adding_and_subtracting_radicals"]}, {"node_count": 1, "nodes": ["number_sense_length_l1"], "representative_cycle": ["number_sense_length_l1", "number_sense_length_l1"]}, {"node_count": 1, "nodes": ["proportions_1"], "representative_cycle": ["proportions_1", "proportions_1"]}]
