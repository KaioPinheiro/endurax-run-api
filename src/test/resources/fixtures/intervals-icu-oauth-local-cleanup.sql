-- Remove somente registros criados pela fixture local identificada por email .invalid.
DELETE e FROM intervals_icu_workout_exports e
JOIN pagamentos p ON p.id = e.pagamento_id
WHERE p.email_pagador = 'oauth-local-fixture@example.invalid';

DELETE c FROM intervals_icu_connections c
JOIN pagamentos p ON p.id = c.pagamento_id
WHERE p.email_pagador = 'oauth-local-fixture@example.invalid';

DELETE s FROM intervals_icu_oauth_states s
JOIN pagamentos p ON p.id = s.pagamento_id
WHERE p.email_pagador = 'oauth-local-fixture@example.invalid';

DELETE FROM pagamentos
WHERE email_pagador = 'oauth-local-fixture@example.invalid';

DELETE FROM training_plans
WHERE nome = 'Plano ficticio para OAuth local'
  AND nivel = 'TESTE'
  AND descricao = 'Fixture local isolada; nao representa um plano real.'
  AND NOT EXISTS (
      SELECT 1 FROM pagamentos p WHERE p.training_plan_id = training_plans.id
  );
