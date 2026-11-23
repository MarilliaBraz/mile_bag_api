-- A tarifa oculta passa a ser escolhida por motorista, nao mais pela empresa toda (ajustes de 2026-11-23).
--
-- Desligada por padrao: o motorista ve a tarifa, como sempre. Oculta, o relatorio dele mostra so os km - ou
-- "Entrega local" - e o pedagio; o fechamento da empresa nao muda.
ALTER TABLE usuario ADD COLUMN ocultar_tarifa BOOLEAN NOT NULL DEFAULT false;

-- Quem ja tinha ligado a opcao geral (V56) nao perde a escolha: os motoristas dessa empresa ficam com a tarifa oculta.
-- Com row_security desligada o UPDATE ou funciona (dono com BYPASSRLS/superusuario) ou FALHA ALTO - nunca deixa de
-- atualizar em silencio por causa da RLS forcada.
SET LOCAL row_security = off;
UPDATE usuario u SET ocultar_tarifa = true
WHERE u.papel = 'MOTORISTA'
  AND EXISTS (SELECT 1 FROM exibicao_tarifa_motorista e WHERE e.tenant_id = u.tenant_id AND e.ocultar_tarifa);

-- A opcao geral da empresa deixa de existir (a escolha agora fica no cadastro de cada motorista).
DROP TABLE exibicao_tarifa_motorista;
