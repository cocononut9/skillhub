ALTER TABLE skill DROP CONSTRAINT skill_resource_type_check;
ALTER TABLE skill ADD CONSTRAINT skill_resource_type_check CHECK (resource_type IN ('SKILL', 'WEB', 'PLUGIN'));
