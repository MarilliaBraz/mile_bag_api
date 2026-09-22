-- T005 (tasks.md desta HU): dado de DESENVOLVIMENTO, nao de producao -
-- viabiliza testar esta HU antes de HU11 (CRUD completo do catalogo)
-- existir. Tenant fixo e reconhecivel (nao corresponde a nenhum tenant
-- real provisionado por HU14); a linha fica orfa e invisivel a qualquer
-- tenant real em producao (RLS nunca casa esse tenant_id por acidente).
--
-- Motivos extraidos literalmente do texto de spec.md (Acceptance Scenario 1).
INSERT INTO motivo_ocorrencia (id, tenant_id, nome, ativo) VALUES
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'Ausencia do recebedor', true),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'Endereco nao localizado', true),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'Recusa de recebimento', true);
