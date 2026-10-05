CREATE TABLE app_users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(80) NOT NULL UNIQUE,
 display_name VARCHAR(120) NOT NULL, password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(15) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 CONSTRAINT ck_user_role CHECK (role IN ('ADMIN','VENTAS'))
) ENGINE=InnoDB;
CREATE TABLE products (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, sku VARCHAR(40) NOT NULL UNIQUE,
 name VARCHAR(120) NOT NULL, category VARCHAR(40) NOT NULL, unit VARCHAR(10) NOT NULL,
 minimum_stock DECIMAL(12,3) NOT NULL, sale_price DECIMAL(12,0) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, description VARCHAR(500),
 CONSTRAINT ck_product_unit CHECK (unit IN ('kg','unidad')),
 CONSTRAINT ck_product_numbers CHECK (minimum_stock >= 0 AND sale_price >= 0)
) ENGINE=InnoDB;
CREATE TABLE locations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL UNIQUE,
 description VARCHAR(250) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE suppliers (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120) NOT NULL,
 contact VARCHAR(100) NOT NULL, phone VARCHAR(40) NOT NULL, notes VARCHAR(250) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE lots (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, location_id BIGINT NOT NULL,
 supplier_id BIGINT, code VARCHAR(60) NOT NULL, quantity DECIMAL(12,3) NOT NULL,
 unit_cost DECIMAL(12,0) NOT NULL, received_on DATE NOT NULL, expires_on DATE,
 quality VARCHAR(20) NOT NULL,
 FOREIGN KEY(product_id) REFERENCES products(id), FOREIGN KEY(location_id) REFERENCES locations(id),
 FOREIGN KEY(supplier_id) REFERENCES suppliers(id),
 CONSTRAINT ck_lot_numbers CHECK(quantity >= 0 AND unit_cost >= 0),
 CONSTRAINT ck_lot_quality CHECK(quality IN ('APTO','RETENIDO')),
 INDEX ix_lot_fefo(product_id, location_id, expires_on), INDEX ix_lot_expiry(expires_on)
) ENGINE=InnoDB;
CREATE TABLE sales (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at DATETIME(6) NOT NULL, actor VARCHAR(80) NOT NULL,
 payment_method VARCHAR(20) NOT NULL, total DECIMAL(14,0) NOT NULL,
 cancelled BOOLEAN NOT NULL DEFAULT FALSE, cancellation_reason VARCHAR(250),
 CONSTRAINT ck_sale_total CHECK(total >= 0), INDEX ix_sale_date(created_at)
) ENGINE=InnoDB;
CREATE TABLE sale_lines (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, sale_id BIGINT NOT NULL, lot_id BIGINT NOT NULL,
 quantity DECIMAL(12,3) NOT NULL, unit_price DECIMAL(12,0) NOT NULL, subtotal DECIMAL(14,0) NOT NULL,
 FOREIGN KEY(sale_id) REFERENCES sales(id), FOREIGN KEY(lot_id) REFERENCES lots(id),
 CONSTRAINT ck_line_numbers CHECK(quantity > 0 AND unit_price >= 0 AND subtotal >= 0)
) ENGINE=InnoDB;
CREATE TABLE movements (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, lot_id BIGINT NOT NULL, sale_id BIGINT,
 created_at DATETIME(6) NOT NULL, kind VARCHAR(30) NOT NULL,
 quantity DECIMAL(12,3) NOT NULL, actor VARCHAR(80) NOT NULL, reason VARCHAR(250) NOT NULL,
 FOREIGN KEY(lot_id) REFERENCES lots(id), FOREIGN KEY(sale_id) REFERENCES sales(id),
 INDEX ix_movement_date(created_at)
) ENGINE=InnoDB;
