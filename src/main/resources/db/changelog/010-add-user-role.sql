-- 添加用户角色字段
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user';

-- 设置 id=1 的用户为管理员
UPDATE users SET role = 'admin' WHERE id = 1;