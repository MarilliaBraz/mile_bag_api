-- HU07/HU11: toda empresa precisa de ao menos um motivo de ocorrencia, ou o
-- motorista fica sem como registrar uma nao entrega ("Nenhum motivo de
-- ocorrencia esta configurado para a sua empresa").
--
-- Empresas novas ja nascem com este catalogo (MotivoOcorrenciaService.
-- criarPadroesSeAusentes, chamado no cadastro - HU14). Aqui o mesmo catalogo
-- vai para as empresas ja existentes que ainda nao tem nenhum motivo; quem ja
-- configurou os seus (mesmo desativados) nao e alterado.
INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo, reagendamento)
SELECT gen_random_uuid(), t.id, padrao.descricao, true, padrao.reagendamento
FROM tenant t
CROSS JOIN (VALUES
    ('Ausência do recebedor', false),
    ('Endereço não localizado', false),
    ('Recusa de recebimento', false),
    ('Reagendada com o passageiro', true)
) AS padrao (descricao, reagendamento)
WHERE NOT EXISTS (SELECT 1 FROM motivo_ocorrencia m WHERE m.tenant_id = t.id);
