package com.marillia.milebag.business.sincronizacao;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.sincronizacao.model.EventoSincronizacaoRequest;
import com.marillia.milebag.business.sincronizacao.model.SincronizarLoteRequest;
import com.marillia.milebag.business.sincronizacao.model.SincronizarLoteResponse;
import com.marillia.milebag.business.sincronizacao.model.StatusEventoResponse;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Endpoints de sincronizacao offline (HU08) - ver contracts/sincronizacao-api.md. */
@RestController
@RequestMapping("/api/v1/sync")
public class SincronizacaoController {

    private final SincronizacaoService sincronizacaoService;

    public SincronizacaoController(SincronizacaoService sincronizacaoService) {
        this.sincronizacaoService = sincronizacaoService;
    }

    @PostMapping("/eventos")
    public SincronizarLoteResponse sincronizar(@RequestBody SincronizarLoteRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        validarLote(request);
        return sincronizacaoService.processarLote(request.eventos(), usuario.userId());
    }

    @GetMapping("/status")
    public List<StatusEventoResponse> status(@RequestParam("idsLocais") List<UUID> idsLocais) {
        return sincronizacaoService.consultarStatus(idsLocais);
    }

    private void validarLote(SincronizarLoteRequest request) {
        if (request == null || request.eventos() == null || request.eventos().isEmpty()) {
            throw new RegraNegocioException("O lote de eventos e obrigatorio.");
        }
        for (EventoSincronizacaoRequest evento : request.eventos()) {
            if (evento.idLocal() == null) {
                throw new RegraNegocioException("Todo evento do lote precisa de idLocal.");
            }
            if (evento.tipoEvento() == null) {
                throw new RegraNegocioException("Todo evento do lote precisa de tipoEvento.");
            }
        }
    }
}
