-- Dado de DESENVOLVIMENTO, nao de producao - mesmo padrao de
-- V13__seed_dev_motivo_ocorrencia.sql: viabiliza testar HU14 (cadastro de
-- tenant) antes de HU16 (CRUD completo de planos) existir.
INSERT INTO plano_assinatura (id, nome, ativo) VALUES
    ('00000000-0000-0000-0000-000000000201', 'Plano Padrao (dev)', true);
