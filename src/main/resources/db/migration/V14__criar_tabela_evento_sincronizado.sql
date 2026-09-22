-- HU08: Operar offline e sincronizar automaticamente.
-- Ver mile_bag_documentacao/specs/HU08-operar-offline-sincronizar/data-model.md
--
-- Chave de deduplicacao dos eventos offline: a propria id_local (UUID v4
-- gerado no dispositivo) e a chave primaria, ja que e naturalmente unica
-- por natureza (nao precisa de um id gerado separado).
CREATE TABLE evento_sincronizado (
    id_local       UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    tipo_evento    VARCHAR(40) NOT NULL
                       CHECK (tipo_evento IN (
                           'BDO_CAPTURADO', 'ENTREGA_AUTOATRIBUIDA', 'ROTA_ESCOLHIDA',
                           'VIAGEM_INICIADA', 'BAGAGEM_CARONA_VINCULADA',
                           'COMPROVANTE_REGISTRADO', 'OCORRENCIA_REGISTRADA')),
    processado_em  TIMESTAMPTZ NOT NULL,
    resultado      VARCHAR(20) NOT NULL
                       CHECK (resultado IN ('ACEITO', 'DUPLICADO', 'ERRO')),
    -- Id do registro de dominio criado por este evento (contracts/sincronizacao-api.md,
    -- campo "idServidor") - usado tambem para resolver referencias entre
    -- eventos do mesmo lote offline (ex.: BAGAGEM_CARONA_VINCULADA
    -- referenciando o BDO de um BDO_CAPTURADO anterior pelo idLocal, antes
    -- de qualquer um dos dois ter sido confirmado pelo servidor).
    id_servidor    UUID
);

CREATE INDEX idx_evento_sincronizado_tenant_id ON evento_sincronizado (tenant_id);

ALTER TABLE evento_sincronizado ENABLE ROW LEVEL SECURITY;
ALTER TABLE evento_sincronizado FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON evento_sincronizado
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
