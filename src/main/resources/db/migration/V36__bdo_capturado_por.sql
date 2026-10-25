-- HU01/HU08: quem fotografou o BDO. A foto tirada sem internet vira um BDO
-- pendente de conferencia so na sincronizacao, quando o motorista ja saiu da
-- tela de captura; sem saber de quem e a foto, nao havia como mostrar a ele
-- as capturas que esperam conferencia. Nulo para BDOs recebidos por e-mail
-- (HU02) e para os capturados antes desta migracao.
ALTER TABLE bdo ADD COLUMN capturado_por UUID;

CREATE INDEX idx_bdo_capturado_por_pendente ON bdo (tenant_id, capturado_por) WHERE status_conferencia = 'PENDENTE';
