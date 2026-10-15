-- SOMENTE PARA BANCO LOCAL DESCARTAVEL. Nunca execute em producao.
SET @fixture_suffix = UUID();
SET @fixture_plan_token = CONCAT('oauth-local-fixture-', @fixture_suffix);

INSERT INTO training_plans (nome, objetivo, nivel, descricao)
VALUES (
    'Plano ficticio para OAuth local',
    'Validar conexao com Intervals.icu',
    'TESTE',
    'Fixture local isolada; nao representa um plano real.'
);
SET @fixture_training_plan_id = LAST_INSERT_ID();

INSERT INTO pagamentos (
    order_external_id,
    external_reference,
    idempotency_key,
    status,
    status_detail,
    valor,
    email_pagador,
    pix_copia_cola,
    qr_code_base64,
    data_expiracao,
    criado_em,
    atualizado_em,
    pago_em,
    plano_gerado,
    geracao_status,
    training_plan_id
) VALUES (
    CONCAT('oauth-local-order-', @fixture_suffix),
    @fixture_plan_token,
    CONCAT('oauth-local-idempotency-', @fixture_suffix),
    'APPROVED',
    'fixture-local',
    12.90,
    'oauth-local-fixture@example.invalid',
    'fixture-sem-pagamento-real',
    'fixture-sem-qrcode-real',
    DATE_ADD(NOW(6), INTERVAL 1 DAY),
    NOW(6),
    NOW(6),
    NOW(6),
    TRUE,
    'COMPLETED',
    @fixture_training_plan_id
);

-- Exibe apenas a URL local de acesso ao plano. Nenhum token OAuth e exibido.
SELECT CONCAT(
    'http://localhost:8080/api/integrations/intervals-icu/plans/',
    @fixture_plan_token,
    '/authorize'
) AS local_authorization_url;
