-- Pagamento de cada motorista em cada fechamento (a quinzena paga de uma vez): a agendar -> agendado -> pago.
-- A empresa marca conforme for pagando; marcar como pago por engano se desfaz com motivo. Sem linha = a agendar.
CREATE TABLE pagamento_motorista (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    fechamento_id   UUID NOT NULL REFERENCES fechamento (id),
    motorista_id    UUID NOT NULL,
    situacao        VARCHAR(20) NOT NULL,
    data_agendada   DATE,
    pago_em         DATE,
    atualizado_por  UUID NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    atualizado_em   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_pagamento_motorista UNIQUE (fechamento_id, motorista_id),
    CONSTRAINT ck_pagamento_motorista_situacao CHECK (situacao IN ('A_AGENDAR', 'AGENDADO', 'PAGO'))
);
CREATE INDEX idx_pagamento_motorista_tenant_id ON pagamento_motorista (tenant_id);
CREATE INDEX idx_pagamento_motorista_situacao ON pagamento_motorista (situacao);
ALTER TABLE pagamento_motorista ENABLE ROW LEVEL SECURITY;
ALTER TABLE pagamento_motorista FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON pagamento_motorista
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Rastro de cada mudanca (agendou, pagou, desfez com motivo): quem e quando. Nunca editado.
CREATE TABLE evento_pagamento_motorista (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    pagamento_motorista_id  UUID NOT NULL REFERENCES pagamento_motorista (id),
    tipo                    VARCHAR(30) NOT NULL,
    data_referencia         DATE,
    motivo                  TEXT,
    usuario_id              UUID NOT NULL,
    data_hora               TIMESTAMPTZ NOT NULL,
    criado_em               TIMESTAMPTZ NOT NULL,
    atualizado_em           TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_evento_pagamento_motorista_tenant_id ON evento_pagamento_motorista (tenant_id);
CREATE INDEX idx_evento_pagamento_motorista_pagamento ON evento_pagamento_motorista (pagamento_motorista_id);
ALTER TABLE evento_pagamento_motorista ENABLE ROW LEVEL SECURITY;
ALTER TABLE evento_pagamento_motorista FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON evento_pagamento_motorista
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
