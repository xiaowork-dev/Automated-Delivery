CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_user_status CHECK (status IN (0,1))
);
CREATE TABLE product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  subtitle VARCHAR(200),
  price DECIMAL(10,2) NOT NULL,
  cover_url VARCHAR(500),
  description TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'OFF_SHELF',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_product_price CHECK (price > 0),
  CONSTRAINT ck_product_status CHECK (status IN ('ON_SALE','OFF_SHELF'))
);
CREATE TABLE order_info (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL,
  pay_type VARCHAR(20) NOT NULL DEFAULT 'MOCK',
  paid_at DATETIME,
  delivered_at DATETIME,
  expire_at DATETIME NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_user FOREIGN KEY(user_id) REFERENCES sys_user(id),
  CONSTRAINT ck_order_amount CHECK (total_amount > 0),
  CONSTRAINT ck_order_status CHECK (status IN ('WAIT_PAY','PAID','DELIVERED','COMPLETED','CANCELLED','EXPIRED'))
);
CREATE INDEX idx_order_user_created ON order_info(user_id, created_at);
CREATE INDEX idx_order_status_expire ON order_info(status, expire_at);
CREATE TABLE order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL UNIQUE,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  subtotal DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_item_order FOREIGN KEY(order_id) REFERENCES order_info(id),
  CONSTRAINT fk_item_product FOREIGN KEY(product_id) REFERENCES product(id),
  CONSTRAINT ck_item_quantity CHECK (quantity = 1)
);
CREATE INDEX idx_item_product ON order_item(product_id);
CREATE TABLE redeem_code (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  code VARCHAR(128) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
  order_id BIGINT UNIQUE,
  assigned_user_id BIGINT,
  assigned_at DATETIME,
  used_by BIGINT,
  used_at DATETIME,
  expired_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_code_product FOREIGN KEY(product_id) REFERENCES product(id),
  CONSTRAINT fk_code_order FOREIGN KEY(order_id) REFERENCES order_info(id),
  CONSTRAINT fk_code_assignee FOREIGN KEY(assigned_user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_code_used_by FOREIGN KEY(used_by) REFERENCES sys_user(id),
  CONSTRAINT ck_code_status CHECK (status IN ('UNUSED','LOCKED','ASSIGNED','USED','EXPIRED','DISABLED'))
);
CREATE INDEX idx_code_stock ON redeem_code(product_id, status, expired_at, id);
CREATE TABLE redeem_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  redeem_code_id BIGINT NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  result VARCHAR(20) NOT NULL,
  reason VARCHAR(200),
  request_id VARCHAR(64),
  ip VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_record_code FOREIGN KEY(redeem_code_id) REFERENCES redeem_code(id),
  CONSTRAINT fk_record_order FOREIGN KEY(order_id) REFERENCES order_info(id),
  CONSTRAINT fk_record_user FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
CREATE INDEX idx_record_user_created ON redeem_record(user_id, created_at);
CREATE TABLE operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT NOT NULL,
  action VARCHAR(50) NOT NULL,
  target_type VARCHAR(30) NOT NULL,
  target_id BIGINT,
  detail VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_log_operator FOREIGN KEY(operator_id) REFERENCES sys_user(id)
);
CREATE INDEX idx_log_created ON operation_log(created_at);
CREATE TABLE delivery_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL UNIQUE,
  code_id BIGINT NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  request_id VARCHAR(64),
  result VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_delivery_order FOREIGN KEY(order_id) REFERENCES order_info(id),
  CONSTRAINT fk_delivery_code FOREIGN KEY(code_id) REFERENCES redeem_code(id),
  CONSTRAINT fk_delivery_user FOREIGN KEY(user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_delivery_product FOREIGN KEY(product_id) REFERENCES product(id)
);
