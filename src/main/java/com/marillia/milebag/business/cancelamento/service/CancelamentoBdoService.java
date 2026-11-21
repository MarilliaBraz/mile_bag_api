package com.marillia.milebag.business.cancelamento.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.cancelamento.model.BdoCancelado;
import com.marillia.milebag.business.cancelamento.model.CancelamentoBdo;
import com.marillia.milebag.business.cancelamento.model.CancelamentoBdoResponse;
import com.marillia.milebag.business.cancelamento.model.CancelarBdoRequest;
import com.marillia.milebag.business.cancelamento.repository.CancelamentoBdoRepository;
import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.entrega.service.EntregaService.EntregaCancelada;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.util.TextoLivre;

/**
 * Cancelamento do BDO pela empresa (V47), com motivo obrigatorio da lista de cancelamento.
 *
 * <ul>
 *   <li>A entrega fica CANCELADA e sem motorista: sai dos disponiveis e do painel; nenhuma acao do motorista e aceita
 *       depois ({@link EntregaService#exigirNaoCancelada}).</li>
 *   <li>Motorista em rota: a viagem e encerrada e vinculada ao cancelamento, sem pagamento.</li>
 *   <li>Caronas: o BDO deixa de ser carona (se a principal ja foi entregue, 409; senao o motorista dela e avisado);
 *       as caronas da entrega cancelada voltam aos disponiveis.</li>
 *   <li>Quem estava com a entrega e avisado por push depois do commit. O passageiro nao e avisado.</li>
 * </ul>
 *
 * Cada efeito fica no seu dominio; aqui so a orquestracao e o registro.
 */
@Service
public class CancelamentoBdoService {

    private static final Logger log = LoggerFactory.getLogger(CancelamentoBdoService.class);

    static final int TAMANHO_MAXIMO_COMPLEMENTO = 500;

    private final CancelamentoBdoRepository cancelamentoBdoRepository;
    private final BdoService bdoService;
    private final EntregaService entregaService;
    private final BagagemCaronaService bagagemCaronaService;
    private final ViagemService viagemService;
    private final MotivoOcorrenciaService motivoOcorrenciaService;
    private final UsuarioConsultaService usuarioConsultaService;
    private final ApplicationEventPublisher eventos;

    public CancelamentoBdoService(
            CancelamentoBdoRepository cancelamentoBdoRepository,
            BdoService bdoService,
            EntregaService entregaService,
            BagagemCaronaService bagagemCaronaService,
            ViagemService viagemService,
            MotivoOcorrenciaService motivoOcorrenciaService,
            UsuarioConsultaService usuarioConsultaService,
            ApplicationEventPublisher eventos) {
        this.cancelamentoBdoRepository = cancelamentoBdoRepository;
        this.bdoService = bdoService;
        this.entregaService = entregaService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.viagemService = viagemService;
        this.motivoOcorrenciaService = motivoOcorrenciaService;
        this.usuarioConsultaService = usuarioConsultaService;
        this.eventos = eventos;
    }

    @Transactional
    public CancelamentoBdoResponse cancelar(UUID bdoId, UUID usuarioId, CancelarBdoRequest request) {
        Bdo bdo = bdoService.buscarPorId(bdoId)
                .filter(encontrado -> encontrado.getStatusConferencia() == StatusConferencia.CONFIRMADO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
        UUID motivoId = request == null ? null : request.motivoId();
        if (motivoId == null) {
            throw new RegraNegocioException("Informe o motivo do cancelamento.");
        }
        // Motivo inativo, de outra empresa ou de outra finalidade: 404, como na ocorrencia e na dispensa.
        MotivoOcorrencia motivo = motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId, FinalidadeMotivo.CANCELAMENTO);
        String complemento = TextoLivre.opcional(request.complemento(), TAMANHO_MAXIMO_COMPLEMENTO, "complemento");
        // Carona: a bagagem viaja com outra entrega. Ja entregue com ela, nao ha o que cancelar (e o km complementar
        // ja faz parte do pagamento); em aberto, quem avisar e o motorista da entrega principal.
        Optional<Entrega> principal = bagagemCaronaService.entregaPrincipalDe(bdoId);
        if (principal.map(entrega -> entrega.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO).orElse(false)) {
            throw new ConflitoException("Esta bagagem ja foi entregue junto com outra entrega e nao pode ser cancelada.");
        }

        // Primeiro a entrega (com a linha travada): se ja foi concluida ou cancelada, nada mais e gravado.
        EntregaCancelada cancelada = entregaService.cancelar(bdoId);
        bagagemCaronaService.soltarDoCancelamento(bdoId, cancelada.entregaId());
        UUID motoristaAvisado = cancelada.motoristaAnterior() != null
                ? cancelada.motoristaAnterior()
                : principal.map(Entrega::getMotoristaResponsavel).orElse(null);
        CancelamentoBdo cancelamento = cancelamentoBdoRepository.save(new CancelamentoBdo(
                bdoId, cancelada.entregaId(), motivo.getId(), complemento, usuarioId, motoristaAvisado,
                cancelada.estavaEmRota(), Instant.now()));
        if (cancelada.estavaEmRota()) {
            viagemService.encerrarPorCancelamento(cancelada.entregaId(), cancelamento.getId());
        }
        if (motoristaAvisado != null) {
            eventos.publishEvent(new BdoCancelado(bdoId, bdo.getNumero(), motoristaAvisado, motivo.getDescricao()));
        }

        log.info("BDO {} cancelado pela empresa (cancelamento {}, motorista avisado {}, em rota: {}).",
                bdoId, cancelamento.getId(), motoristaAvisado, cancelada.estavaEmRota());
        return paraResponse(cancelamento);
    }

    /** Para a rastreabilidade da mala (uso interno, sem checagem de acesso). */
    @Transactional(readOnly = true)
    public Optional<CancelamentoBdoResponse> buscarPorEntrega(UUID entregaId) {
        return cancelamentoBdoRepository.findByEntregaId(entregaId).map(this::paraResponse);
    }

    private CancelamentoBdoResponse paraResponse(CancelamentoBdo cancelamento) {
        return new CancelamentoBdoResponse(
                cancelamento.getId(),
                cancelamento.getBdoId(),
                cancelamento.getEntregaId(),
                motivoOcorrenciaService.descricao(cancelamento.getMotivoId()).orElse(null),
                cancelamento.getComplemento(),
                nome(cancelamento.getCanceladoPor()),
                nome(cancelamento.getMotoristaAnteriorId()),
                cancelamento.isEmRota(),
                cancelamento.getDataHora());
    }

    private String nome(UUID usuarioId) {
        return usuarioId == null ? null : usuarioConsultaService.nomeParaExibicao(usuarioId).orElse(null);
    }
}
