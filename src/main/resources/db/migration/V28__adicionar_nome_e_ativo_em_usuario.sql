-- HU23: cadastro de motoristas pelo administrador do tenant.
-- nome: exibido nas telas (tarifa por motorista, fechamento); nulo nas contas antigas (administradores de HU15).
-- ativo: conta desativada nao entra, mas o historico dela continua apontando para o id.
ALTER TABLE usuario ADD COLUMN nome VARCHAR(200);
ALTER TABLE usuario ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
