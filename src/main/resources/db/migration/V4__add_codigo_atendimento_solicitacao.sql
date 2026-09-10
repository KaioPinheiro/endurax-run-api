ALTER TABLE solicitacoes_plano
    ADD COLUMN codigo_atendimento VARCHAR(10) NULL;

CREATE UNIQUE INDEX uk_solicitacoes_plano_codigo_atendimento
    ON solicitacoes_plano (codigo_atendimento);
