package com.marillia.milebag.business.dadosbdo.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.bdo.model.DadosPassageiroBdo;
import com.marillia.milebag.business.dadosbdo.model.AlterarDadosBdoRequest;
import com.marillia.milebag.business.dadosbdo.service.AlteracaoDadosBdoService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Correcao dos dados do passageiro de um BDO pela empresa (V59) - so o administrador ({@code /api/v1/gestao/**}). */
@RestController
@RequestMapping("/api/v1/gestao/bdos/{bdoId}/dados-passageiro")
public class AlteracaoDadosBdoController {

    private final AlteracaoDadosBdoService alteracaoDadosBdoService;

    public AlteracaoDadosBdoController(AlteracaoDadosBdoService alteracaoDadosBdoService) {
        this.alteracaoDadosBdoService = alteracaoDadosBdoService;
    }

    @GetMapping
    public DadosPassageiroBdo dados(@PathVariable UUID bdoId) {
        return alteracaoDadosBdoService.dados(bdoId);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterar(@PathVariable UUID bdoId, @RequestBody AlterarDadosBdoRequest request,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        alteracaoDadosBdoService.alterar(bdoId, request == null ? null : request.dados(), request == null ? null : request.motivo(),
                usuario.userId());
    }
}
