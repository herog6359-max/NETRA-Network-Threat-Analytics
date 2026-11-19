# Upload demonstration

Use `DEMO_UPLOAD_ATTACK.csv` in Add Demo Data > Upload Traffic Dataset.

This file contains 120 synthetic flow summaries, not captured traffic. SYN counts rise in the second half to demonstrate the rule-based trend projection. No network packets are sent by uploading it.

The timestamp column is deliberately omitted: the existing CSV importer assigns the current time to each row. This keeps the demo within the forecast window on any presentation date. Re-uploading adds another demonstration batch; it does not reset earlier data.

After upload, select Model A (Deterministic Baseline), select 6 steps, and run once. Return to Start Here to view the full trajectory. Check Incident Queue for the resulting alert if projected peak risk reaches 60%. Existing data also affects the forecast, so percentages can vary.

Expected ingestion: 120 total, 120 valid, 0 rejected. Confirm the backend database health is CONNECTED; a job status alone is not persistence proof.
