CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    email      VARCHAR(150)  NOT NULL UNIQUE,
    password   VARCHAR(255)  NOT NULL,
    phone      VARCHAR(20),
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(150)   NOT NULL,
    description    TEXT,
    image_url      VARCHAR(255),
    price          DECIMAL(10, 2) NOT NULL,
    stock_quantity INT            NOT NULL DEFAULT 0,
    active         BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE addresses (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    zip_code     VARCHAR(10)  NOT NULL,
    street       VARCHAR(150) NOT NULL,
    number       VARCHAR(10),
    complement   VARCHAR(100),
    neighborhood VARCHAR(100) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(2)   NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE cart_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL DEFAULT 1,
    UNIQUE (user_id, product_id),
    FOREIGN KEY (user_id)    REFERENCES users (id),
    FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE orders (
    id         BIGINT         AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT         NOT NULL,
    address_id BIGINT         NOT NULL,
    status     VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    total      DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)    REFERENCES users (id),
    FOREIGN KEY (address_id) REFERENCES addresses (id)
);

CREATE TABLE order_items (
    id           BIGINT         AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    product_name VARCHAR(150)   NOT NULL,
    quantity     INT            NOT NULL,
    unit_price   DECIMAL(10, 2) NOT NULL,
    subtotal     DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id)   REFERENCES orders (id),
    FOREIGN KEY (product_id) REFERENCES products (id)
);
