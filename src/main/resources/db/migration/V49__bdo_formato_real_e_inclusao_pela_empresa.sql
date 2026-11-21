-- BDO no formato real (telex WorldTracer) e inclusao manual pela empresa; fim da captura por foto do motorista.
--
-- Os modelos reais trazem varias malas por BDO (TN01, TN02...), endereco em linhas separadas (PA/YP/ST/ZP, ou
-- TA/YT/TS/TZ quando o endereco e temporario), o voo e o aeroporto onde a mala chega e a data prevista de entrega.
-- Os BDOs ja registrados por foto continuam no historico (origem FOTO_MANUAL); so deixa de existir a captura.

-- 1. Quarta origem: a empresa incluiu o BDO pelo painel (colando o texto do telex ou digitando os campos).
ALTER TABLE bdo DROP CONSTRAINT bdo_origem_check;
ALTER TABLE bdo ADD CONSTRAINT bdo_origem_check
    CHECK (origem IN ('FOTO_MANUAL', 'EMAIL_AUTOMATICO', 'COMPANHIA_AEREA', 'EMPRESA'));

-- 2. Varias etiquetas num BDO ("IB723167, IB723211") e o endereco completo, montado com cidade, UF e CEP.
ALTER TABLE bdo ALTER COLUMN etiqueta_bagagem TYPE VARCHAR(300);
ALTER TABLE bdo ALTER COLUMN endereco_entrega TYPE VARCHAR(500);

-- 3. Dados do BDO real. Todos opcionais: nem todo BDO os traz, e os antigos ficam nulos.
ALTER TABLE bdo ADD COLUMN tipo_processo         VARCHAR(10);   -- AHL (mala extraviada), DPR (mala danificada)...
ALTER TABLE bdo ADD COLUMN cidade                VARCHAR(100);
ALTER TABLE bdo ADD COLUMN uf                    VARCHAR(2);
ALTER TABLE bdo ADD COLUMN cep                   VARCHAR(9);
ALTER TABLE bdo ADD COLUMN instrucoes_entrega    TEXT;          -- LD01, LD02, SI: complemento, horario, voo
ALTER TABLE bdo ADD COLUMN aeroporto_retirada    VARCHAR(3);    -- onde o motorista retira a mala (ex.: GYN)
ALTER TABLE bdo ADD COLUMN voo_chegada           VARCHAR(20);   -- voo que traz a mala ate esse aeroporto
ALTER TABLE bdo ADD COLUMN data_entrega_prevista DATE;          -- .DD
ALTER TABLE bdo ADD COLUMN quantidade_malas      INTEGER CHECK (quantidade_malas > 0);
ALTER TABLE bdo ADD COLUMN cores_bagagem         VARCHAR(200);  -- CT01, CT02: cor e tipo de cada mala (ex.: BK22)
ALTER TABLE bdo ADD COLUMN valor_informado       VARCHAR(30);   -- .CS (ex.: "BRL 132"); so informativo
ALTER TABLE bdo ADD COLUMN texto_original        TEXT;          -- o telex como chegou (e-mail ou colado pela empresa)
ALTER TABLE bdo ADD COLUMN incluido_por          UUID;          -- administrador que incluiu (origem EMPRESA)
