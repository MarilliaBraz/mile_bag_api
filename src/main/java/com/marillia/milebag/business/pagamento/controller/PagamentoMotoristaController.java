package com.marillia.milebag.business.pagamento.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.pagamento.model.DataPagamentoRequest;
import com.marillia.milebag.business.pagamento.model.DesfazerPagamentoRequest;
import com.marillia.milebag.business.pagamento.model.PagamentoMotoristaResponse;
import com.marillia.milebag.business.pagamento.service.PagamentoMotoristaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Pagamento da quinzena de cada motorista (V60) - so o administrador do tenant ({@code /api/v1/fechamentos/**}). */
@RestController
@RequestMapping("/api/v1/fechamentos/{fechamentoId}/pagamentos")
public class PagamentoMotoristaController {

    private final PagamentoMotoristaService pagamentoMotoristaService;

    public PagamentoMotoristaController(PagamentoMotoristaService pagamentoMotoristaService) {
        this.pagamentoMotoristaService = pagamentoMotoristaService;
    }

    @GetMapping
    public List<PagamentoMotoristaResponse> listar(@PathVariable UUID fechamentoId) {
        return pagamentoMotoristaService.listar(fechamentoId);
    }

    @PostMapping("/{motoristaId}/agendamento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void agendar(@PathVariable UUID fechamentoId, @PathVariable UUID motoristaId, @RequestBody DataPagamentoRequest request,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        pagamentoMotoristaService.agendar(fechamentoId, motoristaId, request == null ? null : request.data(), usuario.userId());
    }

    @PostMapping("/{motoristaId}/pago")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarPago(@PathVariable UUID fechamentoId, @PathVariable UUID motoristaId,
            @RequestBody(required = false) DataPagamentoRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        pagamentoMotoristaService.marcarPago(fechamentoId, motoristaId, request == null ? null : request.data(), usuario.userId());
    }

    @PostMapping("/{motoristaId}/desfazer-pago")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desfazerPago(@PathVariable UUID fechamentoId, @PathVariable UUID motoristaId, @RequestBody DesfazerPagamentoRequest request,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        pagamentoMotoristaService.desfazerPago(fechamentoId, motoristaId, request == null ? null : request.motivo(), usuario.userId());
    }
}
