ALTER TABLE skill ADD COLUMN resource_type VARCHAR(16) NOT NULL DEFAULT 'SKILL';
ALTER TABLE skill ADD CONSTRAINT skill_resource_type_check CHECK (resource_type IN ('SKILL', 'WEB'));
