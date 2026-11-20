-- Cancelamento do BDO pela empresa (ajustes dos testes de 2026-11-20, item 2).
--
-- A empresa cancela um BDO (enviado por engano, cliente retirou no aeroporto, duplicado...) com motivo obrigatorio da
-- sua lista. O BDO sai dos disponiveis e do painel do motorista; quem estava com a entrega e avisado por push. O
-- cancelamento nao e pago (nem com o motorista em rota), nao conta no volume do plano e nao avisa o passageiro.

-- 1. Terceira lista no mesmo cadastro de motivos.
ALTER TABLE motivo_ocorrencia DROP CONSTRAINT ck_motivo_ocorrencia_finalidade;
ALTER TABLE motivo_ocorrencia ADD CONSTRAINT ck_motivo_ocorrencia_finalidade
    CHECK (finalidade IN ('OCORRENCIA', 'DISPENSA', 'CANCELAMENTO'));

-- Motivos de cancelamento iniciais das empresas ja existentes (as novas recebem os mesmos no cadastro -
-- MotivoOcorrenciaService.criarPadroesSeAusentes).
INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo, reagendamento, finalidade)
SELECT gen_random_uuid(), t.id, padrao.descricao, true, false, 'CANCELAMENTO'
FROM tenant t
CROSS JOIN (VALUES
    ('Enviado por engano'),
    ('Cliente retirou a bagagem no aeroporto'),
    ('BDO duplicado'),
    ('Outro motivo')
) AS padrao (descricao)
WHERE NOT EXISTS (SELECT 1 FROM motivo_ocorrencia m WHERE m.tenant_id = t.id AND m.finalidade = 'CANCELAMENTO');

-- 2. Situacao nova da entrega. O BDO cancelado sem motorista ganha uma entrega ja CANCELADA: a mesma linha unica por
--    BDO (uk_entrega_bdo_id) tira o BDO dos disponiveis e impede a autoatribuicao, sem mudar essas consultas.
ALTER TABLE entrega DROP CONSTRAINT entrega_status_check;
ALTER TABLE entrega ADD CONSTRAINT entrega_status_check
    CHECK (status IN ('AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'EM_ANDAMENTO', 'ENCERRADA_COM_SUCESSO', 'REABERTA', 'CANCELADA'));

-- 3. Registro do cancelamento - um por BDO, nunca editado nem apagado.
CREATE TABLE cancelamento_bdo (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    bdo_id                UUID NOT NULL REFERENCES bdo (id),
    entrega_id            UUID NOT NULL REFERENCES entrega (id),
    motivo_ocorrencia_id  UUID NOT NULL REFERENCES motivo_ocorrencia (id),
    complemento           TEXT,
    cancelado_por         UUID NOT NULL,
    -- Quem estava com a entrega no momento (avisado por push); nulo se ninguem tinha assumido.
    motorista_anterior_id UUID,
    -- Verdadeiro quando o motorista ja estava em rota com a bagagem: a viagem e encerrada sem pagamento.
    em_rota               BOOLEAN NOT NULL,
    data_hora             TIMESTAMPTZ NOT NULL,
    criado_em             TIMESTAMPTZ NOT NULL,
    atualizado_em         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_cancelamento_bdo_bdo_id UNIQUE (bdo_id)
);

CREATE INDEX idx_cancelamento_bdo_tenant_id ON cancelamento_bdo (tenant_id);
CREATE INDEX idx_cancelamento_bdo_entrega_id ON cancelamento_bdo (entrega_id);

ALTER TABLE cancelamento_bdo ENABLE ROW LEVEL SECURITY;
ALTER TABLE cancelamento_bdo FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON cancelamento_bdo
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- 4. A viagem interrompida pelo cancelamento fica no historico, mas NAO e tentativa sem sucesso: nao entra no
--    fechamento (que so paga entrega concluida e trajeto com ocorrencia ou dispensa).
ALTER TABLE trajeto ADD COLUMN cancelamento_id UUID REFERENCES cancelamento_bdo (id);

-- 5. Encaminhamento da companhia aerea (V45): o BDO cancelado encerra o encaminhamento como CANCELADO - a bagagem
--    pode ser encaminhada de novo, como no cancelamento feito pela propria companhia. So o BDO cancelado vira
--    CANCELADO: as caronas da entrega cancelada seguem a regra de sempre e, soltas logo depois
--    (CancelamentoBdoService), voltam a aguardar motorista pelo trigger abaixo.
CREATE OR REPLACE FUNCTION encaminhamento_entrega_pela_entrega() RETURNS trigger AS $$
BEGIN
    UPDATE encaminhamento_entrega
    SET etapa = CASE WHEN NEW.status = 'CANCELADA' AND bdo_id = NEW.bdo_id THEN 'CANCELADO'
                     ELSE etapa_encaminhamento(NEW.status, NEW.motorista_responsavel) END,
        cancelado_em = CASE WHEN NEW.status = 'CANCELADA' AND bdo_id = NEW.bdo_id THEN now() ELSE cancelado_em END,
        atualizado_em = now()
    WHERE etapa <> 'CANCELADO'
      AND (bdo_id = NEW.bdo_id OR bdo_id IN (SELECT bc.bdo_id FROM bagagem_carona bc WHERE bc.entrega_principal_id = NEW.id));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- A carona solta (desvinculada ou liberada pelo cancelamento da principal) volta a aguardar motorista - antes a etapa
-- ficava presa na da entrega principal.
CREATE FUNCTION encaminhamento_entrega_carona_solta() RETURNS trigger AS $$
BEGIN
    UPDATE encaminhamento_entrega
    SET etapa = 'AGUARDANDO_MOTORISTA', atualizado_em = now()
    WHERE etapa <> 'CANCELADO' AND bdo_id = OLD.bdo_id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_encaminhamento_entrega_carona_solta
    AFTER DELETE ON bagagem_carona
    FOR EACH ROW EXECUTE FUNCTION encaminhamento_entrega_carona_solta();
