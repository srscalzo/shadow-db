CREATE TABLE users (
    id          INT NOT NULL AUTO_INCREMENT,
    username    VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_users_email (email)
);
