-- HU11 (data-model.md, tasks.md T002 e contracts/configuracoes-tenant-api.md)
-- usam consistentemente "descricao" para o texto do motivo de ocorrencia;
-- HU07 (V12), que so consumia o catalogo (leitura), tinha chamado a mesma
-- coluna de "nome". HU11 e a dona do CRUD completo desta entidade (o
-- proprio comentario deixado em V12/MotivoOcorrencia.java ja previa isso) -
-- renomeia para alinhar com a spec mais recente e autoritativa, sem perder
-- os dados (incluindo o seed de desenvolvimento de V13).
ALTER TABLE motivo_ocorrencia RENAME COLUMN nome TO descricao;
