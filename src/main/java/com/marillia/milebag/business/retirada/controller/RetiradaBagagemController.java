package com.marillia.milebag.business.retirada.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.retirada.model.RetiradaBagagemResponse;
import com.marillia.milebag.business.retirada.service.RetiradaBagagemService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Retirada da bagagem no aeroporto pelo motorista (V48). */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/retirada")
public class RetiradaBagagemController {

    private final RetiradaBagagemService retiradaBagagemService;

    public RetiradaBagagemController(RetiradaBagagemService retiradaBagagemService) {
        this.retiradaBagagemService = retiradaBagagemService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RetiradaBagagemResponse registrar(
            @PathVariable UUID entregaId,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            @RequestParam(value = "avarias", required = false) List<MultipartFile> avarias,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "dataHora", required = false) String dataHora,
            @RequestParam(value = "idLocal", required = false) String idLocal,
            @RequestParam(value = "observacaoAvaria", required = false) String observacaoAvaria,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return retiradaBagagemService.registrar(
                entregaId, usuario.userId(), foto, avarias, latitude, longitude, dataHora, idLocal, observacaoAvaria);
    }
}
