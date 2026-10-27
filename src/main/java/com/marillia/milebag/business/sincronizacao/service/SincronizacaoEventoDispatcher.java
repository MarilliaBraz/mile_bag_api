package com.marillia.milebag.business.sincronizacao.service;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.EntregaResponse;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.ocorrencia.model.OcorrenciaResponse;
import com.marillia.milebag.business.ocorrencia.service.OcorrenciaService;
import com.marillia.milebag.business.rota.model.SelecionarRotaRequest;
import com.marillia.milebag.business.rota.service.RotaService;
import com.marillia.milebag.business.sincronizacao.model.EventoSincronizacaoRequest;
import com.marillia.milebag.business.sincronizacao.model.EventoSincronizado;
import com.marillia.milebag.business.sincronizacao.model.PayloadBagagemCaronaVinculada;
import com.marillia.milebag.business.sincronizacao.model.PayloadBdoCapturado;
import com.marillia.milebag.business.sincronizacao.model.PayloadComprovanteRegistrado;
import com.marillia.milebag.business.sincronizacao.repository.EventoSincronizadoRepository;
import com.marillia.milebag.business.sincronizacao.model.PayloadEntregaAutoatribuida;
import com.marillia.milebag.business.sincronizacao.model.PayloadOcorrenciaRegistrada;
import com.marillia.milebag.business.sincronizacao.model.PayloadRotaEscolhida;
import com.marillia.milebag.business.sincronizacao.model.PayloadViagemIniciada;
import com.marillia.milebag.business.viagem.model.IniciarViagemRequest;
import com.marillia.milebag.business.viagem.model.TrajetoIniciadoResponse;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Roteia cada evento do lote offline (T009 desta HU) para o servico de
 * dominio equivalente ja implementado em HU01-HU07, reaproveitando
 * integralmente a mesma validacao sincrona - e o unico lugar que conhece a
 * adaptacao de cada payload (base64 para imagem, ids de path movidos para o
 * corpo). Cada despacho e sua propria transacao (nao ha {@code @Transactional}
 * no chamador, {@code SincronizacaoService}), para que a falha de um evento
 * nunca desfaca os que ja foram aceitos no mesmo lote (research.md, secao 4).
 */
@Component
public class SincronizacaoEventoDispatcher {

    private final BdoService bdoService;
    private final EntregaService entregaService;
    private final RotaService rotaService;
    private final ViagemService viagemService;
    private final BagagemCaronaService bagagemCaronaService;
    private final ComprovanteEntregaService comprovanteEntregaService;
    private final OcorrenciaService ocorrenciaService;
    private final EventoSincronizadoRepository eventoSincronizadoRepository;
    private final ObjectMapper objectMapper;

    public SincronizacaoEventoDispatcher(
            BdoService bdoService,
            EntregaService entregaService,
            RotaService rotaService,
            ViagemService viagemService,
            BagagemCaronaService bagagemCaronaService,
            ComprovanteEntregaService comprovanteEntregaService,
            OcorrenciaService ocorrenciaService,
            EventoSincronizadoRepository eventoSincronizadoRepository,
            ObjectMapper objectMapper) {
        this.bdoService = bdoService;
        this.entregaService = entregaService;
        this.rotaService = rotaService;
        this.viagemService = viagemService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.comprovanteEntregaService = comprovanteEntregaService;
        this.ocorrenciaService = ocorrenciaService;
        this.eventoSincronizadoRepository = eventoSincronizadoRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consulta de duplicidade (FR-006) e de status. Fica aqui, e nao direto
     * em {@code SincronizacaoService}, por causa do {@code TenantAspect}: ele
     * so aplica o tenant na conexao quando ja existe uma transacao ativa, e
     * {@code processarLote} e propositalmente nao transacional (cada item
     * tem a sua, em {@link #despachar}). Sem transacao, o RLS via a GUC vazia
     * - erro de cast para uuid numa conexao reaproveitada, ou resultado vazio
     * silencioso numa conexao nova.
     */
    @Transactional(readOnly = true)
    public Optional<EventoSincronizado> buscarProcessado(UUID idLocal) {
        return eventoSincronizadoRepository.findById(idLocal);
    }

    @Transactional
    public UUID despachar(EventoSincronizacaoRequest evento, UUID motoristaId, Map<UUID, UUID> resolvidosNesteLote) {
        UUID idServidor = switch (evento.tipoEvento()) {
            case BDO_CAPTURADO -> despacharBdoCapturado(evento);
            case ENTREGA_AUTOATRIBUIDA -> despacharEntregaAutoatribuida(evento, motoristaId, resolvidosNesteLote);
            case ROTA_ESCOLHIDA -> despacharRotaEscolhida(evento, resolvidosNesteLote);
            case VIAGEM_INICIADA -> despacharViagemIniciada(evento, resolvidosNesteLote);
            case BAGAGEM_CARONA_VINCULADA -> despacharBagagemCaronaVinculada(evento, resolvidosNesteLote);
            case COMPROVANTE_REGISTRADO -> despacharComprovanteRegistrado(evento, resolvidosNesteLote);
            case OCORRENCIA_REGISTRADA -> despacharOcorrenciaRegistrada(evento, resolvidosNesteLote);
        };

        eventoSincronizadoRepository.save(new EventoSincronizado(evento.idLocal(), evento.tipoEvento(), idServidor));
        return idServidor;
    }

    private UUID despacharBdoCapturado(EventoSincronizacaoRequest evento) {
        PayloadBdoCapturado payload = converter(evento, PayloadBdoCapturado.class);
        MultipartFile imagem = new ByteArrayMultipartFile("imagem", payload.nomeArquivo(), decodificar(payload.imagemBase64()));
        BdoResponse resposta = bdoService.capturar(imagem);
        return resposta.id();
    }

    private UUID despacharEntregaAutoatribuida(EventoSincronizacaoRequest evento, UUID motoristaId, Map<UUID, UUID> resolvidos) {
        PayloadEntregaAutoatribuida payload = converter(evento, PayloadEntregaAutoatribuida.class);
        UUID bdoId = resolverReferencia(payload.bdoId(), resolvidos);
        EntregaResponse resposta = entregaService.autoatribuir(bdoId, motoristaId);
        return resposta.id();
    }

    private UUID despacharRotaEscolhida(EventoSincronizacaoRequest evento, Map<UUID, UUID> resolvidos) {
        PayloadRotaEscolhida payload = converter(evento, PayloadRotaEscolhida.class);
        UUID entregaId = resolverReferencia(payload.entregaId(), resolvidos);
        SelecionarRotaRequest request =
                new SelecionarRotaRequest(payload.tipoSelecao(), payload.alternativaId(), payload.tracadoManual(), payload.justificativa());
        rotaService.selecionar(entregaId, request);
        return entregaId;
    }

    private UUID despacharViagemIniciada(EventoSincronizacaoRequest evento, Map<UUID, UUID> resolvidos) {
        PayloadViagemIniciada payload = converter(evento, PayloadViagemIniciada.class);
        UUID entregaId = resolverReferencia(payload.entregaId(), resolvidos);
        TrajetoIniciadoResponse resposta = viagemService.iniciarViagem(entregaId, new IniciarViagemRequest(payload.lat(), payload.lng()));
        return resposta.id();
    }

    private UUID despacharBagagemCaronaVinculada(EventoSincronizacaoRequest evento, Map<UUID, UUID> resolvidos) {
        PayloadBagagemCaronaVinculada payload = converter(evento, PayloadBagagemCaronaVinculada.class);
        UUID entregaId = resolverReferencia(payload.entregaId(), resolvidos);
        UUID bdoId = resolverReferencia(payload.bdoId(), resolvidos);
        BagagemCarona bagagemCarona = bagagemCaronaService.vincular(entregaId, bdoId);
        return bagagemCarona.getId();
    }

    private UUID despacharComprovanteRegistrado(EventoSincronizacaoRequest evento, Map<UUID, UUID> resolvidos) {
        PayloadComprovanteRegistrado payload = converter(evento, PayloadComprovanteRegistrado.class);
        UUID entregaId = resolverReferencia(payload.entregaId(), resolvidos);
        MultipartFile foto = new ByteArrayMultipartFile("foto", payload.nomeArquivo(), decodificar(payload.fotoBase64()));
        ComprovanteEntregaService.ComprovanteEntregaResultado resultado = comprovanteEntregaService.registrar(
                entregaId,
                foto,
                payload.identificacaoRecebedor(),
                payload.dataHora(),
                payload.latitude(),
                payload.longitude(),
                evento.idLocal().toString(),
                payload.justificativaDivergencia());
        ComprovanteEntrega comprovante = resultado.comprovante();
        return comprovante.getId();
    }

    private UUID despacharOcorrenciaRegistrada(EventoSincronizacaoRequest evento, Map<UUID, UUID> resolvidos) {
        PayloadOcorrenciaRegistrada payload = converter(evento, PayloadOcorrenciaRegistrada.class);
        UUID entregaId = resolverReferencia(payload.entregaId(), resolvidos);
        OcorrenciaResponse resposta = ocorrenciaService.registrar(
                entregaId,
                payload.motivoOcorrenciaId(),
                payload.descricaoAdicional(),
                evento.idLocal().toString(),
                JanelaReagendamento.deCampos(payload.janelaInicio(), payload.janelaFim()));
        return resposta.id();
    }

    /**
     * Um campo de referencia (ex.: {@code bdoId}, {@code entregaId}) pode
     * apontar para um registro ja sincronizado antes (ja e o id real) ou
     * para o {@code idLocal} de outro evento do mesmo lote offline, ainda
     * nao confirmado pelo servidor no momento em que o dispositivo gravou
     * os dois eventos na fila local. Tenta resolver como idLocal primeiro
     * (no lote atual, depois em lotes anteriores); se nao encontrar
     * nenhuma correspondencia, assume que o valor ja e um id real.
     */
    private UUID resolverReferencia(UUID valor, Map<UUID, UUID> resolvidosNesteLote) {
        if (valor == null) {
            return null;
        }
        UUID resolvidoNoLote = resolvidosNesteLote.get(valor);
        if (resolvidoNoLote != null) {
            return resolvidoNoLote;
        }
        return eventoSincronizadoRepository.findById(valor).map(EventoSincronizado::getIdServidor).orElse(valor);
    }

    private byte[] decodificar(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new RegraNegocioException("Conteudo de imagem ausente no evento.");
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("Conteudo de imagem em base64 invalido.");
        }
    }

    private <T> T converter(EventoSincronizacaoRequest evento, Class<T> tipo) {
        JsonNode payload = evento.payload();
        if (payload == null || payload.isNull()) {
            throw new RegraNegocioException("Payload ausente no evento " + evento.tipoEvento() + ".");
        }
        try {
            return objectMapper.treeToValue(payload, tipo);
        } catch (JsonProcessingException e) {
            throw new RegraNegocioException("Payload do evento " + evento.tipoEvento() + " invalido: " + e.getMessage());
        }
    }
}
