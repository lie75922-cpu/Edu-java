# DATA-0 Schema Audit

| Source | Rows | Columns | Actual headers | Malformed rows |
| --- | --- | --- | --- | --- |
| Exercise metadata | 837 | 11 | name, live, prerequisites, h_position, v_position, creation_date, seconds_per_fast_problem, pretty_display_name, short_display_name, topic, area | 0 |
| relationship_annotation_training | 1131 | 8 | Exercise_A, Exercise_B, Similarity_avg, Similarity_raw, Difficulty_avg, Difficulty_raw, Prerequisite_avg, Prerequisite_raw | 0 |
| relationship_annotation_testing | 823 | 8 | Exercise_A, Exercise_B, Similarity_avg, Similarity_raw, Difficulty_avg, Difficulty_raw, Prerequisite_avg, Prerequisite_raw | 0 |
| ProblemLog original | 25925992 | 17 | user_id, exercise, problem_type, problem_number, topic_mode, suggested, review_mode, time_done, time_taken, time_taken_attempts, correct, count_attempts, hint_used, count_hints, hint_time_taken_list, earned_proficiency, points_earned | 0 |

## Real samples

- samples/exercise_10.json
- samples/relationship_training_10.json
- samples/relationship_testing_10.json
- samples/problem_log_10.json

All raw files are CSV text. Parsed type below states the DATA-0 validation category. High-cardinality fields use an explicit lower-bound marker rather than a false exact value.

## Exercise metadata columns

| Column | Storage | Parsed type | Missing | Missing rate | Unique |
| --- | --- | --- | --- | --- | --- |
| name | CSV text | text identifier | 0 | 0 | 835 |
| live | CSV text | boolean | 0 | 0 | 2 |
| prerequisites | CSV text | raw comma-delimited text | 95 | 0.113501 | 532 |
| h_position | CSV text | integer | 0 | 0 | 75 |
| v_position | CSV text | integer | 0 | 0 | 54 |
| creation_date | CSV text | timestamp text | 0 | 0 | 837 |
| seconds_per_fast_problem | CSV text | integer | 0 | 0 | 62 |
| pretty_display_name | CSV text | text | 7 | 0.008363 | 827 |
| short_display_name | CSV text | text | 0 | 0 | 819 |
| topic | CSV text | text category | 20 | 0.023895 | 41 |
| area | CSV text | text category | 20 | 0.023895 | 9 |

## Relationship training columns

| Column | Storage | Parsed type | Missing | Missing rate | Unique |
| --- | --- | --- | --- | --- | --- |
| Exercise_A | CSV text | text identifier | 0 | 0 | 239 |
| Exercise_B | CSV text | text identifier | 0 | 0 | 343 |
| Similarity_avg | CSV text | floating-point score | 0 | 0 | 383 |
| Similarity_raw | CSV text | underscore-delimited score text | 0 | 0 | 1123 |
| Difficulty_avg | CSV text | floating-point score | 0 | 0 | 335 |
| Difficulty_raw | CSV text | underscore-delimited score text | 0 | 0 | 1126 |
| Prerequisite_avg | CSV text | floating-point score | 0 | 0 | 371 |
| Prerequisite_raw | CSV text | underscore-delimited score text | 0 | 0 | 1129 |

## Relationship testing columns

| Column | Storage | Parsed type | Missing | Missing rate | Unique |
| --- | --- | --- | --- | --- | --- |
| Exercise_A | CSV text | text identifier | 0 | 0 | 130 |
| Exercise_B | CSV text | text identifier | 0 | 0 | 330 |
| Similarity_avg | CSV text | floating-point score | 0 | 0 | 48 |
| Similarity_raw | CSV text | underscore-delimited score text | 0 | 0 | 285 |
| Difficulty_avg | CSV text | floating-point score | 0 | 0 | 47 |
| Difficulty_raw | CSV text | underscore-delimited score text | 0 | 0 | 381 |
| Prerequisite_avg | CSV text | floating-point score | 0 | 0 | 46 |
| Prerequisite_raw | CSV text | underscore-delimited score text | 0 | 0 | 378 |

## ProblemLog columns

| Column | Storage | Parsed type | Missing | Missing rate | Unique |
| --- | --- | --- | --- | --- | --- |
| user_id | CSV text | integer identifier | 0 | 0 | 247606 |
| exercise | CSV text | text identifier | 0 | 0 | 722 |
| problem_type | CSV text | text category | 81363 | 0.003138 | 1194 |
| problem_number | CSV text | integer | 0 | 0 | 5174 |
| topic_mode | CSV text | boolean | 0 | 0 | 2 |
| suggested | CSV text | boolean | 0 | 0 | 2 |
| review_mode | CSV text | boolean | 0 | 0 | 2 |
| time_done | CSV text | microsecond timestamp | 0 | 0 | at least 2000000 |
| time_taken | CSV text | floating-point seconds | 0 | 0 | 7903 |
| time_taken_attempts | CSV text | raw attempt-duration text | 62365 | 0.002406 | 821841 |
| correct | CSV text | boolean | 0 | 0 | 2 |
| count_attempts | CSV text | integer | 0 | 0 | 386 |
| hint_used | CSV text | boolean | 0 | 0 | 2 |
| count_hints | CSV text | integer | 0 | 0 | 21 |
| hint_time_taken_list | CSV text | raw hint-duration text | 24254263 | 0.935519 | 372527 |
| earned_proficiency | CSV text | boolean | 0 | 0 | 2 |
| points_earned | CSV text | floating-point score | 0 | 0 | 41 |
