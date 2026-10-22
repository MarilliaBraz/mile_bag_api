package com.marillia.milebag.business.emergencia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.emergencia.model.AlertaEmergencia;
import com.marillia.milebag.business.emergencia.model.StatusAlertaEmergencia;
import com.marillia.milebag.core.base.BaseRepository;

/** Isolamento por tenant pela RLS de V34 - nenhuma consulta filtra tenant a mao. */
public interface AlertaEmergenciaRepository extends BaseRepository<AlertaEmergencia, UUID> {

    Optional<AlertaEmergencia> findByMotoristaIdAndStatus(UUID motoristaId, StatusAlertaEmergencia status);

    List<AlertaEmergencia> findByStatusOrderByDataHoraAsc(StatusAlertaEmergencia status);

    /**
     * Serializa, por motorista, o "ver se ja ha alerta aberto e criar" de
     * {@code AlertaEmergenciaService.acionar}: sem isso, um duplo toque faz
     * as duas requisicoes lerem "nenhum aberto" e a segunda bate no indice
     * unico parcial de V34 (500). Mesmo padrao de {@code
     * PropostaRotaRepository.travarPorEntrega}; liberado no fim da transacao.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(cast(:motoristaId as text)))) travado", nativeQuery = true)
    int travarPorMotorista(@Param("motoristaId") UUID motoristaId);

    /** Historico do back-office: os mais recentes primeiro, limitado para nao crescer sem fim na tela. */
    List<AlertaEmergencia> findTop100ByOrderByDataHoraDesc();
}
