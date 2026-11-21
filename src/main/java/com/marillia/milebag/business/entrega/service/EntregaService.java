package com.marillia.milebag.business.entrega.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaRepository;
import com.marillia.milebag.business.entrega.mapper.EntregaMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaResponse;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Autoatribuicao da entrega a partir de um BDO confirmado (US1 desta HU) -
 * ver contracts/entrega-api.md. Tambem e o unico ponto de escrita em {@code
 * Entrega} para os demais dominios (viagem, comprovante, ocorrencia): eles
 * chamam {@link #marcarEmAndamento}/{@link #marcarEncerradaComSucesso}/
 * {@link #reabrirParaNovaTentativa} em vez de mutar a entidade direto,
 * preservando o encapsulamento deste dominio (CONVENTIONS.md 2.1, SRP).
 */
@Service
public class EntregaService {

    private static final Logger log = LoggerFactory.getLogger(EntregaService.class);

    private final EntregaRepository entregaRepository;
    private final BdoService bdoService;
    private final EntregaMapper entregaMapper;
    private final BagagemCaronaRepository bagagemCaronaRepository;
    private final UsuarioConsultaService usuarioConsultaService;

    public EntregaService(
            EntregaRepository entregaRepository, BdoService bdoService, EntregaMapper entregaMapper,
            BagagemCaronaRepository bagagemCaronaRepository, UsuarioConsultaService usuarioConsultaService) {
        this.bagagemCaronaRepository = bagagemCaronaRepository;
        this.usuarioConsultaService = usuarioConsultaService;
        this.entregaRepository = entregaRepository;
        this.bdoService = bdoService;
        this.entregaMapper = entregaMapper;
    }

    /** V52: a entrega do BDO, se ja existe. */
    @Transactional(readOnly = true)
    public Optional<Entrega> buscarPorBdo(UUID bdoId) {
        return entregaRepository.findByBdoId(bdoId);
    }

    @Transactional(readOnly = true)
    public Optional<Entrega> buscarPorId(UUID id) {
        return entregaRepository.findById(id);
    }

    /** V47: o que o motorista ve ao agir numa entrega que a empresa cancelou. */
    public static final String MENSAGEM_CANCELADA = "A empresa cancelou esta entrega.";

    /** V47: toda acao do motorista numa entrega cancelada para aqui, com a mesma mensagem. */
    public static void exigirNaoCancelada(Entrega entrega) {
        if (entrega.getStatus() == EntregaStatus.CANCELADA) {
            throw new ConflitoException(MENSAGEM_CANCELADA);
        }
    }

    @Transactional
    public void marcarEmAndamento(UUID entregaId) {
        Entrega entrega = buscarParaMudarSituacao(entregaId);
        entrega.setStatus(EntregaStatus.EM_ANDAMENTO);
        entregaRepository.save(entrega);
    }

    @Transactional
    public void marcarEncerradaComSucesso(UUID entregaId) {
        Entrega entrega = buscarParaMudarSituacao(entregaId);
        entrega.setStatus(EntregaStatus.ENCERRADA_COM_SUCESSO);
        entregaRepository.save(entrega);
    }

    @Transactional
    /**
     * RN05: sem motorista, a entrega volta a lista de disponiveis (HU19). A
     * janela substitui a anterior - {@code null} a descarta (HU29, RD07).
     */
    public void reabrirParaNovaTentativa(UUID entregaId, JanelaReagendamento janela) {
        Entrega entrega = buscarParaMudarSituacao(entregaId);
        entrega.definirJanelaReagendamento(janela);
        tirarDoMotorista(entrega, EntregaStatus.REABERTA);
    }

    /**
     * V47: cancelamento do BDO pela empresa - a entrega fica CANCELADA, sem motorista. Sem entrega ainda (ninguem
     * assumiu), cria uma ja cancelada, que tira o BDO dos disponiveis. Devolve quem estava com ela e a situacao
     * anterior, para quem cancela avisar o motorista e encerrar a viagem.
     *
     * <p>Com a linha travada: uma autoatribuicao, viagem, ocorrencia ou comprovante simultaneo espera e depois ve a
     * entrega cancelada (ou o cancelamento ve a situacao que ela deixou).</p>
     */
    @Transactional
    public EntregaCancelada cancelar(UUID bdoId) {
        Optional<Entrega> existente = entregaRepository.findForUpdateByBdoId(bdoId);
        if (existente.isEmpty()) {
            UUID id = UUID.randomUUID();
            if (entregaRepository.criarCancelada(id, TenantContext.get(), bdoId) == 0) {
                boolean outroCancelamento = entregaRepository.findByBdoId(bdoId)
                        .map(entrega -> entrega.getStatus() == EntregaStatus.CANCELADA).orElse(false);
                throw new ConflitoException(outroCancelamento
                        ? "Este BDO ja foi cancelado."
                        : "Um motorista acabou de assumir este BDO. Atualize a tela e tente de novo.");
            }
            return new EntregaCancelada(id, null, null);
        }
        Entrega entrega = existente.get();
        if (entrega.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO) {
            throw new ConflitoException("Esta entrega ja foi concluida e nao pode ser cancelada.");
        }
        if (entrega.getStatus() == EntregaStatus.CANCELADA) {
            throw new ConflitoException("Este BDO ja foi cancelado.");
        }
        EntregaCancelada cancelada = new EntregaCancelada(entrega.getId(), entrega.getMotoristaResponsavel(), entrega.getStatus());
        tirarDoMotorista(entrega, EntregaStatus.CANCELADA);
        return cancelada;
    }

    /** Situacao da entrega no instante do cancelamento: {@code motoristaAnterior} e {@code statusAnterior} nulos se ela nao existia. */
    public record EntregaCancelada(UUID entregaId, UUID motoristaAnterior, EntregaStatus statusAnterior) {

        public boolean estavaEmRota() {
            return statusAnterior == EntregaStatus.EM_ANDAMENTO;
        }
    }

    /**
     * V46: a dispensa devolve a entrega aos outros motoristas. Volta como {@code REABERTA} se ja houve tentativa
     * (alguma viagem iniciada) e como {@code AGUARDANDO_ATRIBUICAO} se ninguem chegou a sair com a bagagem. A janela
     * de reagendamento combinada com o passageiro continua valendo para quem assumir.
     *
     * <p>Atomico: so devolve se a entrega ainda for deste motorista e estiver em aberto - uma entrega concluida no
     * mesmo instante nunca e reaberta por engano (409, e a transacao da dispensa inteira e desfeita).</p>
     */
    @Transactional
    public void devolverAposDispensa(UUID entregaId, UUID motoristaId, boolean houveTentativa) {
        EntregaStatus novoStatus = houveTentativa ? EntregaStatus.REABERTA : EntregaStatus.AGUARDANDO_ATRIBUICAO;
        if (entregaRepository.devolverSeAindaDoMotorista(entregaId, motoristaId, novoStatus.name()) == 0) {
            throw new ConflitoException("Esta entrega mudou enquanto voce dispensava. Atualize a tela e confira.");
        }
    }

    private void tirarDoMotorista(Entrega entrega, EntregaStatus status) {
        entrega.setStatus(status);
        entrega.setMotoristaResponsavel(null);
        entregaRepository.save(entrega);
    }

    @Transactional
    public EntregaResponse autoatribuir(UUID bdoId, UUID motoristaId) {
        Bdo bdo = buscarBdoConfirmadoOuFalhar(bdoId);
        // V52: o BDO so vai para os motoristas da base dele (inclusive pela fila offline); sem base, nenhum.
        UUID baseDoMotorista = usuarioConsultaService.baseDoMotorista(motoristaId).orElse(null);
        if (baseDoMotorista == null) {
            throw new ConflitoException("Sua empresa ainda nao definiu a sua base. Peca para ela definir antes de assumir entregas.");
        }
        if (bdo.getBaseId() != null && !bdo.getBaseId().equals(baseDoMotorista)) {
            throw new ConflitoException("Este BDO e de outra base.");
        }
        // Um BDO ja vinculado como bagagem carona viaja junto de outra entrega: nao pode virar entrega propria.
        if (bagagemCaronaRepository.findByBdoId(bdoId).isPresent()) {
            throw new ConflitoException("Este BDO ja esta vinculado como bagagem carona de outra entrega.");
        }

        int atribuidas = entregaRepository.autoatribuirSeDisponivel(
                UUID.randomUUID(), TenantContext.get(), bdoId, motoristaId);
        if (atribuidas == 0) {
            // V47: lista desatualizada (ou evento offline) com o BDO ja cancelado pela empresa - diz o motivo certo.
            entregaRepository.findByBdoId(bdoId).ifPresent(EntregaService::exigirNaoCancelada);
            throw new ConflitoException("Esta entrega ja possui um motorista responsavel.");
        }

        Entrega entrega = entregaRepository.findByBdoId(bdoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Entrega do BDO " + bdoId + " deveria existir apos atribuicao bem sucedida."));

        log.info("Entrega {} autoatribuida ao motorista {} (BDO {})", entrega.getId(), motoristaId, bdoId);
        return entregaMapper.toResponse(entrega, bdo);
    }

    @Transactional(readOnly = true)
    public List<EntregaResponse> listarPorMotorista(UUID motoristaId, EntregaStatus status) {
        return entregaRepository.findByMotoristaResponsavelAndStatus(motoristaId, status).stream()
                .map(entrega -> entregaMapper.toResponse(entrega, buscarBdoOuFalhar(entrega.getBdoId())))
                .toList();
    }

    /** Tela "Entregas" da empresa (V46): as mais recentemente movimentadas primeiro, limitadas; status nulo = todos. */
    @Transactional(readOnly = true)
    public List<Entrega> listarParaGestao(EntregaStatus status) {
        return status == null
                ? entregaRepository.findTop200ByOrderByAtualizadoEmDesc()
                : entregaRepository.findTop200ByStatusOrderByAtualizadoEmDesc(status);
    }

    /** Com a linha travada (V47), e recusando a entrega cancelada pela empresa. */
    /**
     * V51: a entrega travada (FOR UPDATE) ate o fim da transacao - duas correcoes ao mesmo tempo, ou a correcao da
     * retirada e o inicio da viagem, passam uma de cada vez.
     */
    @Transactional
    public Entrega travarParaCorrigir(UUID entregaId) {
        return entregaRepository.findForUpdateById(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
    }

    private Entrega buscarParaMudarSituacao(UUID entregaId) {
        Entrega entrega = entregaRepository.findForUpdateById(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
        exigirNaoCancelada(entrega);
        return entrega;
    }

    /** Travado (V52): a troca de base do mesmo BDO espera o autoatribuir terminar, e vice-versa. */
    private Bdo buscarBdoConfirmadoOuFalhar(UUID bdoId) {
        return bdoService.travar(bdoId)
                .filter(bdo -> bdo.getStatusConferencia() == StatusConferencia.CONFIRMADO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado ou ainda nao confirmado."));
    }

    private Bdo buscarBdoOuFalhar(UUID bdoId) {
        return bdoService.buscarPorId(bdoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
    }
}
