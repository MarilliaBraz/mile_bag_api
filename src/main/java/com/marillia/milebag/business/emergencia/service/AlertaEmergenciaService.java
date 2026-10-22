package com.marillia.milebag.business.emergencia.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.emergencia.model.AlertaEmergencia;
import com.marillia.milebag.business.emergencia.model.AlertaEmergenciaResponse;
import com.marillia.milebag.business.emergencia.model.StatusAlertaEmergencia;
import com.marillia.milebag.business.emergencia.repository.AlertaEmergenciaRepository;
import com.marillia.milebag.business.entrega.model.EntregaResponse;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Alerta de emergencia do motorista (RF23, RD16, HU30) - ver contracts/emergencia-api.md. */
@Service
public class AlertaEmergenciaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaEmergenciaService.class);

    private final AlertaEmergenciaRepository alertaRepository;
    private final EntregaService entregaService;
    private final UsuarioConsultaService usuarioConsultaService;
    private final NotificadorEmergencia notificador;

    public AlertaEmergenciaService(
            AlertaEmergenciaRepository alertaRepository,
            EntregaService entregaService,
            UsuarioConsultaService usuarioConsultaService,
            NotificadorEmergencia notificador) {
        this.alertaRepository = alertaRepository;
        this.entregaService = entregaService;
        this.usuarioConsultaService = usuarioConsultaService;
        this.notificador = notificador;
    }

    /**
     * RD16: um motorista tem no maximo um alerta aberto - acionar de novo
     * (duplo toque, tentar de novo sem resposta) devolve o mesmo, sem avisar
     * os administradores outra vez. O lock por motorista resolve duas
     * requisicoes simultaneas; o indice unico parcial de V34 e a garantia final.
     */
    @Transactional
    public AlertaEmergenciaResponse acionar(UUID motoristaId, Double latitude, Double longitude) {
        validarPosicao(latitude, longitude);
        alertaRepository.travarPorMotorista(motoristaId);
        Optional<AlertaEmergencia> aberto = alertaRepository.findByMotoristaIdAndStatus(motoristaId, StatusAlertaEmergencia.ABERTO);
        if (aberto.isPresent()) {
            log.info("Motorista {} acionou de novo o alerta {} ja aberto - nada criado.", motoristaId, aberto.get().getId());
            return paraResponse(aberto.get(), false);
        }

        UUID entregaEmAndamento = entregaService.listarPorMotorista(motoristaId, EntregaStatus.EM_ANDAMENTO).stream()
                .map(EntregaResponse::id)
                .findFirst()
                .orElse(null);
        AlertaEmergencia alerta =
                alertaRepository.saveAndFlush(new AlertaEmergencia(motoristaId, entregaEmAndamento, latitude, longitude, Instant.now()));
        log.warn("ALERTA DE EMERGENCIA {} acionado pelo motorista {} (posicao {}).", alerta.getId(), motoristaId, alerta.hasPosicao());

        String nome = nomeDoMotorista(motoristaId);
        notificador.notificarAdministradores(alerta, nome);
        return paraResponse(alerta, nome, true);
    }

    @Transactional(readOnly = true)
    public Optional<AlertaEmergenciaResponse> consultarAbertoDoMotorista(UUID motoristaId) {
        return alertaRepository.findByMotoristaIdAndStatus(motoristaId, StatusAlertaEmergencia.ABERTO)
                .map(alerta -> paraResponse(alerta, false));
    }

    @Transactional
    public AlertaEmergenciaResponse cancelar(UUID alertaId, UUID motoristaId) {
        AlertaEmergencia alerta = buscarOuFalhar(alertaId);
        if (!motoristaId.equals(alerta.getMotoristaId())) {
            throw new AcessoNegadoException("Este alerta nao pertence ao motorista autenticado.");
        }
        alerta.cancelar(motoristaId, Instant.now());
        log.info("Alerta de emergencia {} cancelado pelo proprio motorista.", alertaId);
        return paraResponse(alertaRepository.save(alerta), false);
    }

    @Transactional
    public AlertaEmergenciaResponse atender(UUID alertaId, UUID administradorId, String observacao) {
        AlertaEmergencia alerta = buscarOuFalhar(alertaId);
        alerta.atender(administradorId, observacao, Instant.now());
        log.info("Alerta de emergencia {} marcado como atendido por {}.", alertaId, administradorId);
        return paraResponse(alertaRepository.save(alerta), false);
    }

    /** {@code status} nulo: historico (mais recentes primeiro); {@code ABERTO}: os em aberto, mais antigos primeiro. */
    @Transactional(readOnly = true)
    public List<AlertaEmergenciaResponse> listar(StatusAlertaEmergencia status) {
        List<AlertaEmergencia> alertas = status == null
                ? alertaRepository.findTop100ByOrderByDataHoraDesc()
                : alertaRepository.findByStatusOrderByDataHoraAsc(status);
        return alertas.stream().map(alerta -> paraResponse(alerta, false)).toList();
    }

    private void validarPosicao(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new RegraNegocioException("Latitude e longitude devem ser informadas juntas.");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new RegraNegocioException("Coordenadas fora dos limites.");
        }
    }

    private AlertaEmergencia buscarOuFalhar(UUID alertaId) {
        return alertaRepository.findById(alertaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alerta de emergencia nao encontrado."));
    }

    private String nomeDoMotorista(UUID motoristaId) {
        return usuarioConsultaService.nomeParaExibicao(motoristaId).orElse("Motorista");
    }

    private AlertaEmergenciaResponse paraResponse(AlertaEmergencia alerta, boolean novo) {
        return paraResponse(alerta, nomeDoMotorista(alerta.getMotoristaId()), novo);
    }

    private AlertaEmergenciaResponse paraResponse(AlertaEmergencia alerta, String motoristaNome, boolean novo) {
        return new AlertaEmergenciaResponse(
                alerta.getId(),
                alerta.getMotoristaId(),
                motoristaNome,
                alerta.getEntregaId(),
                alerta.getLatitude(),
                alerta.getLongitude(),
                alerta.getDataHora(),
                alerta.getStatus(),
                alerta.getObservacao(),
                alerta.getEncerradoEm(),
                novo);
    }
}
