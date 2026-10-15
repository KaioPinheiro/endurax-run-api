CREATE TABLE intervals_icu_oauth_states (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    state_hash CHAR(64) NOT NULL,
    pagamento_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_intervals_oauth_state_hash UNIQUE (state_hash),
    CONSTRAINT fk_intervals_oauth_state_pagamento
        FOREIGN KEY (pagamento_id) REFERENCES pagamentos(id),
    INDEX idx_intervals_oauth_state_expires_at (expires_at),
    INDEX idx_intervals_oauth_state_pagamento (pagamento_id)
);

CREATE TABLE intervals_icu_connections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pagamento_id BIGINT NOT NULL,
    access_token_encrypted TEXT NOT NULL,
    athlete_id VARCHAR(100) NOT NULL,
    athlete_name VARCHAR(255),
    granted_scope VARCHAR(500) NOT NULL,
    connected_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_intervals_connection_pagamento UNIQUE (pagamento_id),
    CONSTRAINT fk_intervals_connection_pagamento
        FOREIGN KEY (pagamento_id) REFERENCES pagamentos(id),
    INDEX idx_intervals_connection_athlete_id (athlete_id)
);
