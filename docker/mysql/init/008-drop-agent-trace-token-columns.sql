ALTER TABLE agent_trace DROP COLUMN IF EXISTS total_tokens;
ALTER TABLE agent_trace DROP COLUMN IF EXISTS prompt_tokens;
ALTER TABLE agent_trace DROP COLUMN IF EXISTS completion_tokens;
