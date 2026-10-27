-- HU04/HU07: uma viagem por TENTATIVA de entrega, nao por entrega.
-- A ocorrencia reabria a entrega (HU07) sem encerrar a viagem, e o limite de
-- uma viagem por entrega fazia a nova tentativa herdar a viagem antiga, ainda
-- aberta - quilometragem e marcos das duas tentativas misturados no mesmo
-- trajeto. Agora a viagem da tentativa sem sucesso e encerrada na ocorrencia,
-- fica com quem dirigiu e e paga a ele no fechamento (decisao de 2026-10-24).

-- So pode haver uma viagem EM_EXECUCAO por entrega; as encerradas ficam como historico das tentativas.
ALTER TABLE trajeto DROP CONSTRAINT uk_trajeto_entrega_id;
CREATE UNIQUE INDEX uk_trajeto_entrega_em_execucao ON trajeto (entrega_id) WHERE status = 'EM_EXECUCAO';
CREATE INDEX idx_trajeto_entrega ON trajeto (tenant_id, entrega_id);

-- Quem dirigiu: a entrega reaberta fica sem motorista responsavel, mas a viagem continua sendo de quem a fez.
ALTER TABLE trajeto ADD COLUMN motorista_id UUID;
UPDATE trajeto t SET motorista_id = e.motorista_responsavel FROM entrega e WHERE e.id = t.entrega_id;

-- Quando a viagem foi encerrada e, se foi por ocorrencia, qual - e o que a separa da tentativa atual.
ALTER TABLE trajeto ADD COLUMN encerrado_em TIMESTAMPTZ;
ALTER TABLE trajeto ADD COLUMN ocorrencia_id UUID;
CREATE INDEX idx_trajeto_encerrado_por_ocorrencia ON trajeto (tenant_id, encerrado_em) WHERE ocorrencia_id IS NOT NULL;

-- Viagens que ficaram abertas em entregas ja reabertas (o defeito acima): encerradas com o ultimo calculo de
-- rota e vinculadas a ocorrencia mais recente da entrega.
WITH aberta_em_entrega_reaberta AS (
    SELECT t.id AS trajeto_id, c.km_ida, c.km_volta, c.pracas_pedagio, c.tempo_estimado, o.id AS ocorrencia_id, o.data_hora
    FROM trajeto t
    JOIN entrega e ON e.id = t.entrega_id
    CROSS JOIN LATERAL (SELECT * FROM calculo_rota cr WHERE cr.trajeto_id = t.id ORDER BY cr.data_hora DESC LIMIT 1) c
    CROSS JOIN LATERAL (SELECT * FROM ocorrencia oc WHERE oc.entrega_id = e.id ORDER BY oc.data_hora DESC LIMIT 1) o
    WHERE t.status = 'EM_EXECUCAO'
      AND e.status = 'REABERTA'
)
UPDATE trajeto t
SET status = 'ENCERRADO',
    km_ida_final = a.km_ida,
    km_volta_final = a.km_volta,
    pracas_pedagio_final = a.pracas_pedagio,
    tempo_final = a.tempo_estimado,
    encerrado_em = a.data_hora,
    ocorrencia_id = a.ocorrencia_id
FROM aberta_em_entrega_reaberta a
WHERE t.id = a.trajeto_id;

-- Fechamento: um item por viagem paga (a entrega concluida e, agora, cada tentativa sem sucesso).
ALTER TABLE item_fechamento ADD COLUMN trajeto_id UUID;
ALTER TABLE item_fechamento ADD COLUMN tentativa_sem_sucesso BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE item_fechamento DROP CONSTRAINT uk_item_fechamento_entrega;
CREATE UNIQUE INDEX uk_item_fechamento_entrega_concluida ON item_fechamento (fechamento_id, entrega_id) WHERE NOT tentativa_sem_sucesso;
CREATE UNIQUE INDEX uk_item_fechamento_trajeto ON item_fechamento (fechamento_id, trajeto_id) WHERE trajeto_id IS NOT NULL;
