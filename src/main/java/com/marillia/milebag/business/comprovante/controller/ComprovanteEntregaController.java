package com.marillia.milebag.business.comprovante.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 201 para um comprovante novo; 200 quando e o reenvio do mesmo {@code idLocal} (nada foi criado). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComprovanteEntregaResponse> registrar(
            @PathVariable UUID entregaId,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            @RequestParam(value = "identificacaoRecebedor", required = false) String identificacaoRecebedor,
            @RequestParam(value = "dataHora", required = false) String dataHora,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "idLocal", required = false) String idLocal,
            @RequestParam(value = "justificativaDivergencia", required = false) String justificativaDivergencia,
            // V48: passo a passo da entrega (foto na porta, quem assinou, avarias).
            @RequestParam(value = "fotoPorta", required = false) MultipartFile fotoPorta,
            @RequestParam(value = "tipoRecebedorId", required = false) UUID tipoRecebedorId,
            @RequestParam(value = "nomeRecebedor", required = false) String nomeRecebedor,
            @RequestParam(value = "avarias", required = false) List<MultipartFile> avarias,
            @RequestParam(value = "observacaoAvaria", required = false) String observacaoAvaria) {
        var resultado = comprovanteEntregaService.registrar(
                entregaId, foto, identificacaoRecebedor, dataHora, latitude, longitude, idLocal, justificativaDivergencia,
                new ComprovanteEntregaService.DadosDaEntrega(fotoPorta, tipoRecebedorId, nomeRecebedor, avarias, observacaoAvaria));
        return ResponseEntity.status(resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK).body(ComprovanteEntregaResponse.de(resultado.comprovante()));
    }
}
