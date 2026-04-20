CREATE TABLE staff_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_staff_users PRIMARY KEY (id),
    CONSTRAINT uk_staff_users_username UNIQUE (username)
);

CREATE TABLE staff_user_roles (
    staff_user_id BIGINT      NOT NULL,
    role          VARCHAR(50) NOT NULL,
    CONSTRAINT pk_staff_user_roles PRIMARY KEY (staff_user_id, role),
    CONSTRAINT fk_staff_user_roles_user
        FOREIGN KEY (staff_user_id) REFERENCES staff_users(id)
);

-- Default admin account. Password is BCrypt hash of "changeme" at cost 12.
-- Operators MUST change this password immediately in any real deployment.
INSERT INTO staff_users (username, password_hash, full_name, active, created_at, updated_at)
VALUES ('admin', '$2a$12$bukdhbJecsWc/e/Yun7XkOkMrdU8uBW/pduF9LCc.8pMQHwoFezii', 'System Administrator', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO staff_user_roles (staff_user_id, role) SELECT id, 'ROLE_ADMIN' FROM staff_users WHERE username = 'admin';
