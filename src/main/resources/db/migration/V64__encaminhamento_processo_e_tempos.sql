-- Painel da companhia aerea (fase 1): relogio regulatorio da ANAC e cadeia de custodia.
-- processo_aberto_em: quando o passageiro abriu o processo (PIR) - o prazo da ANAC conta dai (7 dias domestico,
-- 21 internacional). tipo_voo: DOMESTICO ou INTERNACIONAL. Os encaminhamentos antigos ficam com o registro da
-- bagagem (ou o encaminhamento) como abertura e voo domestico.
ALTER TABLE encaminhamento_entrega ADD COLUMN processo_aberto_em TIMESTAMPTZ;
ALTER TABLE encaminhamento_entrega ADD COLUMN tipo_voo VARCHAR(15) NOT NULL DEFAULT 'DOMESTICO'
    CHECK (tipo_voo IN ('DOMESTICO', 'INTERNACIONAL'));
-- Hora de cada passo do parceiro, gravada pelo gatilho abaixo quando a etapa muda.
ALTER TABLE encaminhamento_entrega ADD COLUMN coletado_em TIMESTAMPTZ;
ALTER TABLE encaminhamento_entrega ADD COLUMN entregue_em TIMESTAMPTZ;
ALTER TABLE encaminhamento_entrega ADD COLUMN nao_entregue_em TIMESTAMPTZ;

-- bagagem_encontrada tem RLS: a migracao roda como dono das tabelas, entao le todas as companhias.
UPDATE encaminhamento_entrega e
SET processo_aberto_em = COALESCE((SELECT b.registrada_em FROM bagagem_encontrada b WHERE b.id = e.bagagem_id), e.encaminhado_em);
UPDATE encaminhamento_entrega SET processo_aberto_em = encaminhado_em WHERE processo_aberto_em IS NULL;
ALTER TABLE encaminhamento_entrega ALTER COLUMN processo_aberto_em SET NOT NULL;

-- Aproximacao para o que ja aconteceu: a ultima atualizacao.
UPDATE encaminhamento_entrega SET entregue_em = atualizado_em, coletado_em = atualizado_em WHERE etapa = 'ENTREGUE';
UPDATE encaminhamento_entrega SET coletado_em = atualizado_em WHERE etapa = 'A_CAMINHO';
UPDATE encaminhamento_entrega SET nao_entregue_em = atualizado_em WHERE etapa = 'NAO_ENTREGUE';

CREATE FUNCTION encaminhamento_marca_tempos() RETURNS trigger AS $$
BEGIN
    IF NEW.etapa IS DISTINCT FROM OLD.etapa THEN
        IF NEW.etapa = 'A_CAMINHO' AND NEW.coletado_em IS NULL THEN
            NEW.coletado_em := now();
        ELSIF NEW.etapa = 'ENTREGUE' THEN
            NEW.entregue_em := now();
            NEW.coletado_em := COALESCE(NEW.coletado_em, now());
        ELSIF NEW.etapa = 'NAO_ENTREGUE' THEN
            NEW.nao_entregue_em := now();
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_encaminhamento_marca_tempos
    BEFORE UPDATE OF etapa ON encaminhamento_entrega
    FOR EACH ROW EXECUTE FUNCTION encaminhamento_marca_tempos();
