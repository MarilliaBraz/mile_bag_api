package com.marillia.milebag.business.ocorrencia.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.business.ocorrencia.model.Ocorrencia;
import com.marillia.milebag.business.ocorrencia.model.OcorrenciaResponse;
import com.marillia.milebag.business.ocorrencia.repository.OcorrenciaRepository;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Registra ocorrencias de nao entrega e reabre a entrega para nova
 * tentativa (US1 desta HU) - ver contracts/ocorrencia-api.md.
 */
@Service
public class OcorrenciaService {

    private static final Logger log = LoggerFactory.getLogger(OcorrenciaService.class);

    private final OcorrenciaRepository ocorrenciaRepository;
    private final MotivoOcorrenciaService motivoOcorrenciaService;
    private final EntregaService entregaService;
    private final MensagemService mensagemService;
    private final ViagemService viagemService;

    public OcorrenciaService(
            OcorrenciaRepository ocorrenciaRepository,
            MotivoOcorrenciaService motivoOcorrenciaService,
            EntregaService entregaService,
            MensagemService mensagemService,
            ViagemService viagemService) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.motivoOcorrenciaService = motivoOcorrenciaService;
        this.entregaService = entregaService;
        this.mensagemService = mensagemService;
        this.viagemService = viagemService;
    }

    /** {@code novo}: distingue um registro recem-criado de um reenvio idempotente (mesmo idLocal), para o controller escolher 201 ou 200. */
    public record ResultadoRegistro(OcorrenciaResponse ocorrencia, boolean novo) {
    }

    @Transactional
    public OcorrenciaResponse registrar(
            UUID entregaId, UUID motivoOcorrenciaId, String descricaoAdicional, String idLocal, JanelaReagendamento janela) {
        return registrarComResultado(entregaId, motivoOcorrenciaId, descricaoAdicional, idLocal, janela).ocorrencia();
    }

    @Transactional
    public ResultadoRegistro registrarComResultado(
            UUID entregaId, UUID motivoOcorrenciaId, String descricaoAdicional, String idLocal, JanelaReagendamento janela) {
        Entrega entrega = buscarEntregaOuFalhar(entregaId);
        if (entrega.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO) {
            throw new ConflitoException("Esta entrega ja foi encerrada com comprovante de entrega.");
        }

        boolean temIdLocal = idLocal != null && !idLocal.isBlank();
        if (temIdLocal) {
            Optional<Ocorrencia> existente = ocorrenciaRepository.findByEntregaIdAndIdLocal(entregaId, idLocal);
            if (existente.isPresent()) {
                log.info("Ocorrencia da entrega {} ja registrada com o mesmo idLocal - retorno idempotente.", entregaId);
                return new ResultadoRegistro(paraResponse(existente.get(), entrega.getStatus()), false);
            }
        }

        // RLS (motivo_ocorrencia) ja garante que um motivo de outro tenant e
        // invisivel aqui - buscarAtivoOuFalhar retorna 404 tanto para "nao
        // existe" quanto para "existe, mas e de outro tenant" (nunca
        // revelado como tal, mesma convencao do resto da API).
        MotivoOcorrencia motivo = motivoOcorrenciaService.buscarAtivoOuFalhar(motivoOcorrenciaId);
        validarJanela(motivo, janela);

        Ocorrencia ocorrencia = ocorrenciaRepository.save(
                new Ocorrencia(entregaId, motivo.getId(), descricaoAdicional, Instant.now(), temIdLocal, idLocal, janela));

        // A viagem desta tentativa termina aqui (e e paga a quem dirigiu); a nova tentativa comeca outra.
        viagemService.encerrarPorOcorrencia(entregaId, ocorrencia.getId());
        entregaService.reabrirParaNovaTentativa(entregaId, janela);

        log.info(
                "Ocorrencia {} registrada para a entrega {} (motivo {}); entrega reaberta para nova tentativa.",
                ocorrencia.getId(), entregaId, motivo.getId());
        notificarAutomaticaComSeguranca(
                entregaId, janela == null ? TipoEventoNotificacao.OCORRENCIA_REGISTRADA : TipoEventoNotificacao.REAGENDAMENTO_REGISTRADO);
        return new ResultadoRegistro(paraResponse(ocorrencia, EntregaStatus.REABERTA), true);
    }

    /** FR-005 (HU18/HU20): notificacao automatica nunca bloqueia nem falha o registro da ocorrencia. */
    private void notificarAutomaticaComSeguranca(UUID entregaId, TipoEventoNotificacao tipoEvento) {
        try {
            mensagemService.notificarAutomatica(entregaId, tipoEvento);
        } catch (RuntimeException e) {
            log.warn("Falha ao notificar automaticamente a entrega {} da ocorrencia - ocorrencia ja registrada, seguindo.", entregaId, e);
        }
    }

    /**
     * RD07/HU29: a janela e obrigatoria para motivo de reagendamento e
     * proibida nos demais. Que o fim ainda nao tenha passado e checado so no
     * app: um registro feito offline pode chegar depois da janela.
     */
    private void validarJanela(MotivoOcorrencia motivo, JanelaReagendamento janela) {
        if (motivo.isReagendamento() && janela == null) {
            throw new RegraNegocioException("Este motivo e de reagendamento: informe a data e o horario combinados com o passageiro.");
        }
        if (!motivo.isReagendamento() && janela != null) {
            throw new RegraNegocioException("Janela de reagendamento so pode ser informada para um motivo de reagendamento.");
        }
    }

    @Transactional(readOnly = true)
    public List<OcorrenciaResponse> listarPorEntrega(UUID entregaId) {
        Entrega entrega = buscarEntregaOuFalhar(entregaId);
        return ocorrenciaRepository.findByEntregaIdOrderByDataHoraAsc(entregaId).stream()
                .map(ocorrencia -> paraResponse(ocorrencia, entrega.getStatus()))
                .toList();
    }

    private Entrega buscarEntregaOuFalhar(UUID entregaId) {
        return entregaService.buscarPorId(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
    }

    private OcorrenciaResponse paraResponse(Ocorrencia ocorrencia, EntregaStatus statusAtual) {
        return new OcorrenciaResponse(
                ocorrencia.getId(),
                ocorrencia.getEntregaId(),
                ocorrencia.getMotivoOcorrenciaId(),
                ocorrencia.getDataHora(),
                statusAtual,
                ocorrencia.getJanelaInicio(),
                ocorrencia.getJanelaFim());
    }
}
