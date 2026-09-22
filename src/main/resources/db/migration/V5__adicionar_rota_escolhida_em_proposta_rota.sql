-- HU04: ViagemService.iniciarViagem precisa dos dados (distancia, tempo,
-- indicador de pedagio) da alternativa efetivamente escolhida em
-- POST /rotas/selecionar (HU03) para criar o CalculoRota inicial - a V4
-- so guardava tipo_selecao/justificativa, nao a alternativa em si.
ALTER TABLE proposta_rota ADD COLUMN rota_escolhida JSONB;
