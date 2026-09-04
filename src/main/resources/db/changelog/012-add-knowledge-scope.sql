-- changeset offerpilot:012-knowledge-scope
-- comment: 知识库文档增加 scope 字段，区分用户级（user）和系统级（system）文档

ALTER TABLE knowledge_base ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'user';

CREATE INDEX idx_knowledge_base_scope ON knowledge_base (scope);

COMMENT ON COLUMN knowledge_base.scope IS '作用域：user=用户级（默认），system=系统级（管理员维护）';