CREATE TABLE IF NOT EXISTS training_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255),
    objetivo VARCHAR(255),
    nivel VARCHAR(255),
    descricao TEXT
);

CREATE TABLE solicitacoes_plano (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    dados_formulario_json LONGTEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    INDEX idx_solicitacoes_plano_status (status)
);

ALTER TABLE pagamentos
    ADD COLUMN geracao_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN geracao_mensagem VARCHAR(255),
    ADD COLUMN solicitacao_plano_id BIGINT,
    ADD COLUMN training_plan_id BIGINT,
    ADD CONSTRAINT fk_pagamentos_solicitacao_plano
        FOREIGN KEY (solicitacao_plano_id) REFERENCES solicitacoes_plano(id),
    ADD CONSTRAINT fk_pagamentos_training_plan
        FOREIGN KEY (training_plan_id) REFERENCES training_plans(id),
    ADD CONSTRAINT uk_pagamentos_training_plan UNIQUE (training_plan_id),
    ADD CONSTRAINT uk_pagamentos_solicitacao_plano UNIQUE (solicitacao_plano_id),
    ADD INDEX idx_pagamentos_geracao_status (geracao_status),
    ADD INDEX idx_pagamentos_solicitacao_plano (solicitacao_plano_id);
