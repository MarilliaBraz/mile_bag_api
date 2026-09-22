package com.marillia.milebag.business.comprovante.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.comprovante.model.ComprovanteEntregaResponse;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;

/** Endpoint de registro do comprovante de entrega (HU06) - ver contracts/comprovante-entrega-api.md. */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/comprovante")
public class ComprovanteEntregaController {

    private final ComprovanteEntregaService comprovanteEntregaService;

    public ComprovanteEntregaController(ComprovanteEntregaService comprovanteEntregaService) {
        this.comprovanteEntregaService = comprovanteEntregaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ComprovanteEntregaResponse registrar(
            @PathVariable UUID entregaId,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            @RequestParam(value = "identificacaoRecebedor", required = false) String identificacaoRecebedor,
            @RequestParam(value = "dataHora", required = false) String dataHora,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "idLocal", required = false) String idLocal,
            @RequestParam(value = "justificativaDivergencia", required = false) String justificativaDivergencia) {
        var resultado = comprovanteEntregaService.registrar(
                entregaId, foto, identificacaoRecebedor, dataHora, latitude, longitude, idLocal, justificativaDivergencia);
        return ComprovanteEntregaResponse.de(resultado.comprovante());
    }
}
