-- Outreach workflow: when the lead was first contacted and when to follow up next
ALTER TABLE lead ADD COLUMN contacted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE lead ADD COLUMN follow_up_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_lead_follow_up ON lead (follow_up_at);
