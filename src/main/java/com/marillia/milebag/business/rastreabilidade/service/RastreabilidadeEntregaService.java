package com.marillia.milebag.business.rastreabilidade.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.cancelamento.service.CancelamentoBdoService;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.dispensa.model.DispensaEntregaResponse;
import com.marillia.milebag.business.dispensa.service.DispensaEntregaService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.ocorrencia.service.OcorrenciaService;
import com.marillia.milebag.business.fotoentrega.service.FotoEntregaService;
import com.marillia.milebag.business.rastreabilidade.model.EntregaGestaoResponse;
import com.marillia.milebag.business.rastreabilidade.model.FotoGestaoResponse;
import com.marillia.milebag.business.retirada.model.RetiradaBagagem;
import com.marillia.milebag.business.retirada.service.RetiradaBagagemService;
import com.marillia.milebag.business.rastreabilidade.model.EventoRastreabilidade;
import com.marillia.milebag.business.rastreabilidade.model.EventoRastreabilidade.TipoEventoRastreabilidade;
import com.marillia.milebag.business.rastreabilidade.model.HistoricoEntregaResponse;
import com.marillia.milebag.business.segundavia.service.SegundaViaBdoService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Tela "Entregas" da empresa e a rastreabilidade da mala (V46): junta, sem duplicar regra nenhuma, o que cada
 * dominio ja registra - BDO, viagens por tentativa, ocorrencias, dispensas e comprovante - numa linha do tempo.
 * Somente leitura; cada fato continua sendo escrito so pelo seu dominio.
 */
@Service
public class RastreabilidadeEntregaService {

    static final String TIPO_BDO_ASSINADO = "BDO_ASSINADO";

    private final EntregaService entregaService;
    private final BdoService bdoService;
    private final ViagemService viagemService;
    private final OcorrenciaService ocorrenciaService;
    private final DispensaEntregaService dispensaEntregaService;
    private final ComprovanteEntregaService comprovanteEntregaService;
    private final MotivoOcorrenciaService motivoOcorrenciaService;
    private final UsuarioConsultaService usuarioConsultaService;
    private final CancelamentoBdoService cancelamentoBdoService;
    private final BagagemCaronaService bagagemCaronaService;
    private final RetiradaBagagemService retiradaBagagemService;
    private final FotoEntregaService fotoEntregaService;
    private final SegundaViaBdoService segundaViaBdoService;

    public RastreabilidadeEntregaService(
            EntregaService entregaService,
            BdoService bdoService,
            ViagemService viagemService,
            OcorrenciaService ocorrenciaService,
            DispensaEntregaService dispensaEntregaService,
            ComprovanteEntregaService comprovanteEntregaService,
            MotivoOcorrenciaService motivoOcorrenciaService,
            UsuarioConsultaService usuarioConsultaService,
            CancelamentoBdoService cancelamentoBdoService,
            BagagemCaronaService bagagemCaronaService,
            RetiradaBagagemService retiradaBagagemService,
            FotoEntregaService fotoEntregaService,
            SegundaViaBdoService segundaViaBdoService) {
        this.entregaService = entregaService;
        this.bdoService = bdoService;
        this.viagemService = viagemService;
        this.ocorrenciaService = ocorrenciaService;
        this.dispensaEntregaService = dispensaEntregaService;
        this.comprovanteEntregaService = comprovanteEntregaService;
        this.motivoOcorrenciaService = motivoOcorrenciaService;
        this.usuarioConsultaService = usuarioConsultaService;
        this.cancelamentoBdoService = cancelamentoBdoService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.retiradaBagagemService = retiradaBagagemService;
        this.fotoEntregaService = fotoEntregaService;
        this.segundaViaBdoService = segundaViaBdoService;
    }

    @Transactional(readOnly = true)
    public List<EntregaGestaoResponse> listar(EntregaStatus status) {
        Map<UUID, String> nomes = new HashMap<>();
        List<Entrega> entregas = entregaService.listarParaGestao(status);
        Map<UUID, Bdo> bdos = bdoService.buscarPorIds(entregas.stream().map(Entrega::getBdoId).toList());
        // V47: o BDO que ninguem assumiu ainda nao tem entrega, mas a empresa precisa ve-lo para poder cancela-lo.
        List<Bdo> semEntrega = status == null || status == EntregaStatus.AGUARDANDO_ATRIBUICAO ? bdoService.listarSemEntrega() : List.of();
        Set<UUID> caronas = bagagemCaronaService.bdosQueSaoCarona(semEntrega.stream().map(Bdo::getId).toList());
        return Stream.concat(
                        entregas.stream().map(entrega -> resumo(entrega, bdos.get(entrega.getBdoId()), nomes)),
                        semEntrega.stream().map(bdo -> resumoSemEntrega(bdo, caronas.contains(bdo.getId()))))
                .sorted(Comparator.comparing(EntregaGestaoResponse::atualizadoEm, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public HistoricoEntregaResponse historico(UUID entregaId) {
        Entrega entrega = entregaService.buscarPorId(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
        Map<UUID, String> nomes = new HashMap<>();
        List<EventoRastreabilidade> eventos = new ArrayList<>();

        bdoService.buscarPorId(entrega.getBdoId())
                .filter(bdo -> bdo.getDataCaptura() != null)
                .ifPresent(bdo -> eventos.add(new EventoRastreabilidade(
                        TipoEventoRastreabilidade.BDO_RECEBIDO, bdo.getDataCaptura(), null, bdo.getOrigem().name(), null)));

        for (RetiradaBagagem retirada : retiradaBagagemService.listarPorEntrega(entregaId)) {
            eventos.add(new EventoRastreabilidade(
                    TipoEventoRastreabilidade.BAGAGEM_RETIRADA, retirada.getDataHora(), nome(retirada.getMotoristaId(), nomes), null,
                    retirada.getObservacaoAvaria()));
        }

        // A ocorrencia nao guarda o motorista: ele vem da viagem que ela encerrou.
        Map<UUID, UUID> motoristaPorOcorrencia = new HashMap<>();
        for (Trajeto viagem : viagemService.listarViagensDaEntrega(entregaId)) {
            eventos.add(new EventoRastreabilidade(
                    TipoEventoRastreabilidade.VIAGEM_INICIADA, viagem.getDataInicio(), nome(viagem.getMotoristaId(), nomes), null, null));
            if (viagem.getOcorrenciaId() != null && viagem.getMotoristaId() != null) {
                motoristaPorOcorrencia.put(viagem.getOcorrenciaId(), viagem.getMotoristaId());
            }
        }

        ocorrenciaService.listarRegistrosPorEntrega(entregaId).forEach(ocorrencia -> eventos.add(new EventoRastreabilidade(
                TipoEventoRastreabilidade.OCORRENCIA,
                ocorrencia.getDataHora(),
                nome(motoristaPorOcorrencia.get(ocorrencia.getId()), nomes),
                motivoOcorrenciaService.descricao(ocorrencia.getMotivoOcorrenciaId()).orElse(null),
                ocorrencia.getDescricaoAdicional())));

        for (DispensaEntregaResponse dispensa : dispensaEntregaService.listarPorEntrega(entregaId)) {
            eventos.add(new EventoRastreabilidade(
                    dispensa.comBagagem() ? TipoEventoRastreabilidade.DISPENSA_COM_BAGAGEM : TipoEventoRastreabilidade.DISPENSA_SEM_BAGAGEM,
                    dispensa.dataHora(), dispensa.motoristaNome(), dispensa.motivo(), dispensa.complemento()));
        }

        cancelamentoBdoService.buscarPorEntrega(entregaId).ifPresent(cancelamento -> eventos.add(new EventoRastreabilidade(
                TipoEventoRastreabilidade.CANCELADO, cancelamento.dataHora(), cancelamento.canceladoPorNome(), cancelamento.motivo(),
                cancelamento.complemento())));

        // V50: cada envio da segunda via por e-mail - quem mandou e para onde (o PDF tem dados pessoais do passageiro).
        segundaViaBdoService.listarEnvios(entrega.getBdoId()).forEach(envio -> eventos.add(new EventoRastreabilidade(
                TipoEventoRastreabilidade.SEGUNDA_VIA_ENVIADA, envio.getDataHora(), nome(envio.getEnviadoPor(), nomes),
                envio.getDestinatario(), null)));

        comprovanteEntregaService.buscarPorEntrega(entregaId).ifPresent(comprovante -> eventos.add(new EventoRastreabilidade(
                TipoEventoRastreabilidade.ENTREGUE, comprovante.getDataHora(), nome(entrega.getMotoristaResponsavel(), nomes),
                comprovante.getIdentificacaoRecebedor(), null)));

        eventos.sort(Comparator.comparing(EventoRastreabilidade::dataHora, Comparator.nullsLast(Comparator.naturalOrder())));
        return new HistoricoEntregaResponse(resumo(entrega, bdoService.buscarPorId(entrega.getBdoId()).orElse(null), nomes), eventos);
    }

    /** V48: as fotos da entrega, na ordem em que foram tiradas, inclusive a do BDO assinado (do comprovante). */
    @Transactional(readOnly = true)
    public List<FotoGestaoResponse> fotos(UUID entregaId) {
        exigirEntrega(entregaId);
        List<FotoGestaoResponse> fotos = new ArrayList<>(fotoEntregaService.listarPorEntrega(entregaId).stream()
                .map(foto -> new FotoGestaoResponse(foto.getId(), foto.getTipo().name(), foto.getDataHora()))
                .toList());
        comprovanteEntregaService.buscarPorEntrega(entregaId).ifPresent(comprovante ->
                fotos.add(new FotoGestaoResponse(comprovante.getId(), TIPO_BDO_ASSINADO, comprovante.getDataHora())));
        fotos.sort(Comparator.comparing(FotoGestaoResponse::dataHora, Comparator.nullsLast(Comparator.naturalOrder())));
        return fotos;
    }

    /** O arquivo de uma foto desta entrega (a do BDO assinado tem o id do comprovante). */
    @Transactional(readOnly = true)
    public FotoEntregaService.Arquivo foto(UUID entregaId, UUID fotoId) {
        exigirEntrega(entregaId);
        return comprovanteEntregaService.buscarPorEntrega(entregaId)
                .filter(comprovante -> comprovante.getId().equals(fotoId))
                .map(comprovante -> fotoEntregaService.carregarCaminho(comprovante.getFotoDocumentoUrl()))
                .orElseGet(() -> fotoEntregaService.carregar(entregaId, fotoId));
    }

    private void exigirEntrega(UUID entregaId) {
        entregaService.buscarPorId(entregaId).orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
    }

    private EntregaGestaoResponse resumo(Entrega entrega, Bdo bdo, Map<UUID, String> nomes) {
        return new EntregaGestaoResponse(
                entrega.getId(),
                entrega.getBdoId(),
                false,
                entrega.getStatus(),
                bdo == null ? null : bdo.getNumero(),
                bdo == null ? null : bdo.getPassageiroNome(),
                bdo == null ? null : bdo.getEnderecoEntrega(),
                nome(entrega.getMotoristaResponsavel(), nomes),
                entrega.getAtualizadoEm());
    }

    private static EntregaGestaoResponse resumoSemEntrega(Bdo bdo, boolean bagagemCarona) {
        return new EntregaGestaoResponse(
                null,
                bdo.getId(),
                bagagemCarona,
                EntregaStatus.AGUARDANDO_ATRIBUICAO,
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                null,
                bdo.getDataCaptura() != null ? bdo.getDataCaptura() : bdo.getAtualizadoEm());
    }

    /** Nome para exibir, consultado uma vez por pessoa na mesma resposta. */
    private String nome(UUID usuarioId, Map<UUID, String> cache) {
        if (usuarioId == null) {
            return null;
        }
        return cache.computeIfAbsent(usuarioId, id -> usuarioConsultaService.nomeParaExibicao(id).orElse(null));
    }
}
