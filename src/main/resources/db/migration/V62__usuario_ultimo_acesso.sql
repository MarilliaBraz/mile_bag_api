-- Painel de gestao (fase 3): ultima vez que o motorista usou o app, para contar quem esta online por praca.
-- Gravado no maximo uma vez por minuto por conta; nulo = ainda nao usou desde esta versao.
ALTER TABLE usuario ADD COLUMN ultimo_acesso_em TIMESTAMPTZ;
