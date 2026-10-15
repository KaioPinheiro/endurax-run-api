CREATE TABLE intervals_icu_workout_exports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pagamento_id BIGINT NOT NULL,
    workout_key VARCHAR(150) NOT NULL,
    external_id VARCHAR(150) NOT NULL,
    intervals_event_id BIGINT NOT NULL,
    workout_name VARCHAR(255) NOT NULL,
    scheduled_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_intervals_workout_export_key UNIQUE (pagamento_id, workout_key),
    CONSTRAINT uk_intervals_workout_external_id UNIQUE (external_id),
    CONSTRAINT fk_intervals_workout_export_pagamento
        FOREIGN KEY (pagamento_id) REFERENCES pagamentos(id),
    INDEX idx_intervals_workout_export_event (intervals_event_id)
);
