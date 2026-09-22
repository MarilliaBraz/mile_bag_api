-- CalculoRota e CheckpointGps (V7/V8) extendem BaseEntity, que sempre mapeia
-- uma coluna atualizado_em (@UpdateTimestamp) - ficou de fora daquelas
-- migracoes por essas entidades serem tratadas como imutaveis (sem
-- setters), mas o Hibernate ainda grava um valor nela na criacao.
ALTER TABLE calculo_rota ADD COLUMN atualizado_em TIMESTAMPTZ;
UPDATE calculo_rota SET atualizado_em = criado_em WHERE atualizado_em IS NULL;
ALTER TABLE calculo_rota ALTER COLUMN atualizado_em SET NOT NULL;

ALTER TABLE checkpoint_gps ADD COLUMN atualizado_em TIMESTAMPTZ;
UPDATE checkpoint_gps SET atualizado_em = criado_em WHERE atualizado_em IS NULL;
ALTER TABLE checkpoint_gps ALTER COLUMN atualizado_em SET NOT NULL;
