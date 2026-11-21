package com.marillia.milebag.business.dispensa.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.dispensa.model.DispensaEntrega;
import com.marillia.milebag.business.dispensa.model.DispensaEntregaResponse;
import com.marillia.milebag.business.dispensa.model.DispensarEntregaRequest;
import com.marillia.milebag.business.dispensa.repository.DispensaEntregaRepository;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.retirada.service.RetiradaBagagemService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.util.TextoLivre;

/**
 * Dispensa da entrega pelo motorista responsavel (V46): devolve a entrega aos outros motoristas e registra o fato
 * na rastreabilidade da mala.
 *
 * <ul>
 *   <li>Motivo (da lista de dispensa da empresa) obrigatorio se a entrega ja teve alguma viagem - o motorista ja
 *       saiu com a bagagem nesta tentativa ou esta e uma nova tentativa. Na primeira tentativa, antes de retirar a
 *       bagagem, pode dispensar sem motivo.</li>
 *   <li>Em rota (viagem iniciada), a viagem e encerrada como tentativa sem sucesso e paga a quem dirigiu; sem
 *       viagem iniciada nao ha o que pagar.</li>
 * </ul>
 *
 * Reaproveita o encerramento de viagem da ocorrencia ({@link ViagemService#encerrarPorDispensa}) e a devolucao da
 * entrega ({@link EntregaService#devolverAposDispensa}); nao avisa o passageiro, porque a entrega continua de pe.
 */
@Service
public class DispensaEntregaService {

    private static final Logger log = LoggerFactory.getLogger(DispensaEntregaService.class);

    /** So entrega com motorista e ainda nao concluida pode ser dispensada. */
    private static final Set<EntregaStatus> STATUS_DISPENSAVEIS = Set.of(EntregaStatus.ATRIBUIDA, EntregaStatus.EM_ANDAMENTO);
    static final int TAMANHO_MAXIMO_COMPLEMENTO = 500;

    private final DispensaEntregaRepository dispensaEntregaRepository;
    private final EntregaService entregaService;
    private final ViagemService viagemService;
    private final MotivoOcorrenciaService motivoOcorrenciaService;
    private final UsuarioConsultaService usuarioConsultaService;
    private final RetiradaBagagemService retiradaBagagemService;

    public DispensaEntregaService(
            DispensaEntregaRepository dispensaEntregaRepository,
            EntregaService entregaService,
            ViagemService viagemService,
            MotivoOcorrenciaService motivoOcorrenciaService,
            UsuarioConsultaService usuarioConsultaService,
            RetiradaBagagemService retiradaBagagemService) {
        this.dispensaEntregaRepository = dispensaEntregaRepository;
        this.entregaService = entregaService;
        this.viagemService = viagemService;
        this.motivoOcorrenciaService = motivoOcorrenciaService;
        this.usuarioConsultaService = usuarioConsultaService;
        this.retiradaBagagemService = retiradaBagagemService;
    }

    /** Se a dispensa desta entrega exige motivo: a entrega ja teve alguma viagem (nesta ou numa tentativa anterior). */
    @Transactional(readOnly = true)
    public boolean exigeMotivo(UUID entregaId) {
        return viagemService.houveTentativa(entregaId);
    }

    @Transactional
    public DispensaEntregaResponse dispensar(UUID entregaId, UUID motoristaId, DispensarEntregaRequest request) {
        Entrega entrega = entregaService.buscarPorId(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
        EntregaService.exigirNaoCancelada(entrega);
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new ConflitoException("So o motorista responsavel pode dispensar esta entrega.");
        }
        if (!STATUS_DISPENSAVEIS.contains(entrega.getStatus())) {
            throw new ConflitoException("Esta entrega nao pode mais ser dispensada.");
        }

        boolean houveTentativa = viagemService.houveTentativa(entregaId);
        boolean emRota = viagemService.viagemEmAndamento(entregaId);
        UUID motivoId = request == null ? null : request.motivoId();
        if (motivoId == null && houveTentativa) {
            throw new RegraNegocioException("Informe o motivo da dispensa: a bagagem ja saiu com um motorista.");
        }
        if (motivoId != null) {
            // Motivo inativo, de outra empresa ou de ocorrencia: 404, como na ocorrencia.
            motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId, FinalidadeMotivo.DISPENSA);
        }

        // Primeiro a devolucao atomica: se a entrega mudou no meio do caminho, nada mais e gravado.
        entregaService.devolverAposDispensa(entregaId, motoristaId, houveTentativa);
        // V48: a bagagem retirada e nao levada volta; quem assumir de novo retira outra vez.
        retiradaBagagemService.descartarPendentes(entregaId);
        DispensaEntrega dispensa = dispensaEntregaRepository.save(new DispensaEntrega(
                entregaId, motoristaId, motivoId, complemento(request), emRota, Instant.now()));
        if (emRota) {
            viagemService.encerrarPorDispensa(entregaId, dispensa.getId());
        }

        log.info("Entrega {} dispensada pelo motorista {} (dispensa {}, em rota: {}); devolvida aos motoristas.",
                entregaId, motoristaId, dispensa.getId(), emRota);
        return paraResponse(dispensa);
    }

    /**
     * Historico de dispensas para quem pede pela API: a empresa, o motorista responsavel ou um motorista olhando uma
     * entrega disponivel (sem motorista, prestes a assumir). Os demais motoristas nao veem o historico de entrega alheia.
     */
    @Transactional(readOnly = true)
    public List<DispensaEntregaResponse> listarParaUsuario(UUID entregaId, UUID usuarioId, String papel) {
        if (!Papeis.ADMINISTRADOR_TENANT.equals(papel)) {
            UUID responsavel = entregaService.buscarPorId(entregaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."))
                    .getMotoristaResponsavel();
            if (responsavel != null && !responsavel.equals(usuarioId)) {
                throw new AcessoNegadoException("Esta entrega nao pertence ao motorista autenticado.");
            }
        }
        return listarPorEntrega(entregaId);
    }

    /** Historico de dispensas da entrega, da mais antiga para a mais recente (uso interno, sem checagem de acesso). */
    @Transactional(readOnly = true)
    public List<DispensaEntregaResponse> listarPorEntrega(UUID entregaId) {
        return dispensaEntregaRepository.findByEntregaIdOrderByDataHoraAsc(entregaId).stream().map(this::paraResponse).toList();
    }

    private static String complemento(DispensarEntregaRequest request) {
        return request == null ? null : TextoLivre.opcional(request.complemento(), TAMANHO_MAXIMO_COMPLEMENTO, "complemento");
    }

    private DispensaEntregaResponse paraResponse(DispensaEntrega dispensa) {
        String motivo = dispensa.getMotivoId() == null ? null : motivoOcorrenciaService.descricao(dispensa.getMotivoId()).orElse(null);
        return new DispensaEntregaResponse(
                dispensa.getId(),
                dispensa.getEntregaId(),
                usuarioConsultaService.nomeParaExibicao(dispensa.getMotoristaId()).orElse(null),
                motivo,
                dispensa.getComplemento(),
                dispensa.isComBagagem(),
                dispensa.getDataHora());
    }
}
