-- HU22/HU23: o login (usuario.email) precisa ser unico no sistema TODO, nao so por tenant, porque o
-- formulario de entrada nao informa a empresa. Um indice unico vale entre tenants mesmo com RLS
-- (a checagem de unicidade nao passa pela politica), entao fecha a corrida entre dois cadastros
-- simultaneos que a checagem em codigo (AutenticacaoService.loginJaExiste) deixava aberta.
-- Falha se ja houver logins repetidos entre tenants: nesse caso corrija os dados antes de migrar.
CREATE UNIQUE INDEX uk_usuario_email_lower ON usuario (lower(email));

-- HU23: sessoes emitidas antes deste instante deixam de valer (motorista desativado ou senha
-- redefinida). Nulo = nenhuma revogacao.
ALTER TABLE usuario ADD COLUMN tokens_validos_desde TIMESTAMPTZ;
