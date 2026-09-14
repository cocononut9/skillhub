-- Category is independent of RECOMMENDED / PRIVILEGED permissions.
ALTER TABLE label_definition
    ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'GENERAL'
    CHECK (category IN ('GENERAL', 'WORKFLOW', 'ROLE'));

-- First-version catalog. Reuse exact Chinese display-name matches (including
-- existing noncanonical slugs), preserving resource associations and permissions.
CREATE TEMPORARY TABLE label_category_seed (
    slug VARCHAR(64), category VARCHAR(16), zh_name VARCHAR(128),
    en_name VARCHAR(128), sort_order INTEGER
);
INSERT INTO label_category_seed VALUES
    ('workflow-market-insight', 'WORKFLOW', '市场洞察', 'Market insight', 10),
    ('workflow-product-development', 'WORKFLOW', '产品研发', 'Product development', 20),
    ('workflow-finished-goods-procurement', 'WORKFLOW', '成品采购', 'Finished goods procurement', 30),
    ('workflow-quality-inspection', 'WORKFLOW', '品质质检', 'Quality inspection', 40),
    ('workflow-cross-border-logistics', 'WORKFLOW', '跨境物流', 'Cross-border logistics', 50),
    ('workflow-overseas-warehousing', 'WORKFLOW', '海外仓储', 'Overseas warehousing', 60),
    ('workflow-brand-marketing', 'WORKFLOW', '品牌营销', 'Brand marketing', 70),
    ('workflow-platform-operations', 'WORKFLOW', '平台运营', 'Platform operations', 80),
    ('workflow-multichannel-fulfillment', 'WORKFLOW', '多渠道发货', 'Multichannel fulfillment', 90),
    ('workflow-customer-service', 'WORKFLOW', '客户服务', 'Customer service', 100),
    ('role-brand-specialist', 'ROLE', '品牌专员', 'Brand specialist', 110),
    ('role-product-manager', 'ROLE', '产品经理', 'Product manager', 120),
    ('role-procurement-specialist', 'ROLE', '采购专员', 'Procurement specialist', 130),
    ('role-quality-inspector', 'ROLE', '质检专员', 'Quality inspector', 140),
    ('role-logistics-specialist', 'ROLE', '物流专员', 'Logistics specialist', 150),
    ('role-warehouse-specialist', 'ROLE', '仓储专员', 'Warehouse specialist', 160),
    ('role-platform-operator', 'ROLE', '平台运营专员', 'Platform operations specialist', 170),
    ('role-influencer-operator', 'ROLE', '红人运营', 'Influencer operations', 180),
    ('role-social-media-operator', 'ROLE', '社媒运营', 'Social media operations', 190),
    ('role-customer-service-specialist', 'ROLE', '客服专员', 'Customer service specialist', 200);

UPDATE label_definition d
SET category = seed.category
FROM label_category_seed seed
WHERE d.category = 'GENERAL'
  AND EXISTS (
      SELECT 1 FROM label_translation t
      WHERE t.label_id = d.id AND LOWER(t.locale) IN ('zh', 'zh-cn')
        AND t.display_name = seed.zh_name
  );

INSERT INTO label_definition (slug, type, category, visible_in_filter, sort_order)
SELECT seed.slug, 'RECOMMENDED', seed.category, TRUE, seed.sort_order
FROM label_category_seed seed
WHERE NOT EXISTS (SELECT 1 FROM label_definition d WHERE d.slug = seed.slug)
  AND NOT EXISTS (
      SELECT 1 FROM label_translation t
      WHERE LOWER(t.locale) IN ('zh', 'zh-cn') AND t.display_name = seed.zh_name
  );

INSERT INTO label_translation (label_id, locale, display_name)
SELECT d.id, 'zh', seed.zh_name
FROM label_category_seed seed
JOIN label_definition d ON d.slug = seed.slug AND d.category = seed.category
WHERE NOT EXISTS (SELECT 1 FROM label_translation t WHERE t.label_id = d.id AND t.locale = 'zh');

INSERT INTO label_translation (label_id, locale, display_name)
SELECT d.id, 'en', seed.en_name
FROM label_category_seed seed
JOIN label_definition d ON d.slug = seed.slug AND d.category = seed.category
WHERE NOT EXISTS (SELECT 1 FROM label_translation t WHERE t.label_id = d.id AND t.locale = 'en');

DROP TABLE label_category_seed;

