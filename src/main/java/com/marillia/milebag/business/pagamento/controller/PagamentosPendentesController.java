package com.marillia.milebag.business.pagamento.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.pagamento.model.PagamentoPendenteResponse;
import com.marillia.milebag.business.pagamento.service.PagamentoMotoristaService;

/** Painel de atividades da empresa: pagamentos ainda nao feitos, de todas as quinzenas (so o administrador do tenant). */
@RestController
public class PagamentosPendentesController {

    private final PagamentoMotoristaService pagamentoMotoristaService;

    public PagamentosPendentesController(PagamentoMotoristaService pagamentoMotoristaService) {
        this.pagamentoMotoristaService = pagamentoMotoristaService;
    }

    @GetMapping("/api/v1/fechamentos/pagamentos-pendentes")
    public List<PagamentoPendenteResponse> listar() {
        return pagamentoMotoristaService.pendentes();
    }
}
