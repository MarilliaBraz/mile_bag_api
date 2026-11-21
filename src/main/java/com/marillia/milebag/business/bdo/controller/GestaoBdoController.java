package com.marillia.milebag.business.bdo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.model.IncluirBdoRequest;
import com.marillia.milebag.business.bdo.model.LerTextoBdoRequest;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Inclusao do BDO pela empresa (V49) - so o administrador do tenant ({@code /api/v1/gestao/**} no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/gestao/bdos")
public class GestaoBdoController {

    private final BdoService bdoService;

    public GestaoBdoController(BdoService bdoService) {
        this.bdoService = bdoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BdoResponse incluir(@RequestBody IncluirBdoRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return bdoService.criarPelaEmpresa(request.campos(), request.textoOriginal(), usuario.userId());
    }

    /** Le o telex colado e devolve os campos para conferir - nao grava nada. */
    @PostMapping("/leitura")
    public CamposExtraidosBdo ler(@RequestBody LerTextoBdoRequest request) {
        return bdoService.lerTexto(request.texto());
    }

    /** BDOs ja registrados com o numero: o painel avisa antes de incluir um repetido. */
    @GetMapping
    public List<BdoResponse> buscarPorNumero(@RequestParam String numero) {
        return bdoService.buscarPorNumero(numero);
    }
}
