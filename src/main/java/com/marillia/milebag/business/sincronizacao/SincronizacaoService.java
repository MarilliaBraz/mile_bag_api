package com.marillia.milebag.business.sincronizacao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marillia.milebag.business.sincronizacao.model.EventoSincronizacaoRequest;
import com.marillia.milebag.business.sincronizacao.model.EventoSincronizado;
import com.marillia.milebag.business.sincronizacao.model.ResultadoEventoResponse;
import com.marillia.milebag.business.sincronizacao.model.ResultadoSincronizacao;
import com.marillia.milebag.business.sincronizacao.model.SincronizarLoteResponse;
import com.marillia.milebag.business.sincronizacao.model.StatusEventoResponse;

/**
 * Processa o lote de eventos offline (US1 desta HU) - ver
 * contracts/sincronizacao-api.md. Propositalmente sem
 * {@code @Transactional}: cada evento e despachado em sua propria
 * transacao ({@code SincronizacaoEventoDispatcher.despachar}), para que
 * "melhor esforco item a item" (research.md, secao 4) seja real - a falha
 * de um evento nunca desfaz os que ja foram aceitos no mesmo lote.
 */
@Service
public class SincronizacaoService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoService.class);

    private final EventoSincronizadoRepository eventoSincronizadoRepository;
    private final SincronizacaoEventoDispatcher dispatcher;

    public SincronizacaoService(EventoSincronizadoRepository eventoSincronizadoRepository, SincronizacaoEventoDispatcher dispatcher) {
        this.eventoSincronizadoRepository = eventoSincronizadoRepository;
        this.dispatcher = dispatcher;
    }

    public SincronizarLoteResponse processarLote(List<EventoSincronizacaoRequest> eventos, UUID motoristaId) {
        // FR-005: a ordem de processamento e sequenciaLocal, nao a ordem de
        // chegada no array (o cliente ja deveria mandar ordenado, mas o
        // servidor nao confia nisso).
        List<EventoSincronizacaoRequest> ordenados =
                eventos.stream().sorted(Comparator.comparingLong(EventoSincronizacaoRequest::sequenciaLocal)).toList();

        Map<UUID, UUID> resolvidosNesteLote = new HashMap<>();
        List<ResultadoEventoResponse> resultados = new ArrayList<>();

        for (EventoSincronizacaoRequest evento : ordenados) {
            Optional<EventoSincronizado> existente = eventoSincronizadoRepository.findById(evento.idLocal());
            if (existente.isPresent()) {
                UUID idServidor = existente.get().getIdServidor();
                resolvidosNesteLote.put(evento.idLocal(), idServidor);
                resultados.add(new ResultadoEventoResponse(evento.idLocal(), ResultadoSincronizacao.DUPLICADO, idServidor, null));
                continue;
            }

            try {
                UUID idServidor = dispatcher.despachar(evento, motoristaId, resolvidosNesteLote);
                resolvidosNesteLote.put(evento.idLocal(), idServidor);
                resultados.add(new ResultadoEventoResponse(evento.idLocal(), ResultadoSincronizacao.ACEITO, idServidor, null));
            } catch (RuntimeException e) {
                log.warn(
                        "Falha ao processar evento offline {} ({}): {}",
                        evento.idLocal(), evento.tipoEvento(), e.getMessage());
                resultados.add(new ResultadoEventoResponse(evento.idLocal(), ResultadoSincronizacao.ERRO, null, e.getMessage()));
            }
        }

        return new SincronizarLoteResponse(resultados);
    }

    public List<StatusEventoResponse> consultarStatus(List<UUID> idsLocais) {
        return idsLocais.stream()
                .map(idLocal -> eventoSincronizadoRepository.findById(idLocal)
                        .map(evento -> new StatusEventoResponse(idLocal, ResultadoSincronizacao.ACEITO))
                        .orElseGet(() -> new StatusEventoResponse(idLocal, ResultadoSincronizacao.NAO_ENCONTRADO)))
                .toList();
    }
}
