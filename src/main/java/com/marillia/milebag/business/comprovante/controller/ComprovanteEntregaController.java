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

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.correcao.model.CorrecaoInformada;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.exception.RegraNegocioException;
/** Endpoint de registro do comprovante de entrega (HU06) - ver contracts/comprovante-entrega-api.md. */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/comprovante")
public class ComprovanteEntregaController {

    private static final TypeReference<List<CorrecaoInformada>> LISTA_DE_CORRECOES = new TypeReference<>() {
    };

    private final ComprovanteEntregaService comprovanteEntregaService;
    private final ObjectMapper objectMapper;

    public ComprovanteEntregaController(ComprovanteEntregaService comprovanteEntregaService, ObjectMapper objectMapper) {
        this.comprovanteEntregaService = comprovanteEntregaService;
        this.objectMapper = objectMapper;
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
            @RequestParam(value = "observacaoAvaria", required = false) String observacaoAvaria,
            // V51: correcoes feitas antes de finalizar (JSON), com motivo.
            @RequestParam(value = "correcoes", required = false) String correcoes,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        var resultado = comprovanteEntregaService.registrar(
                entregaId, foto, identificacaoRecebedor, dataHora, latitude, longitude, idLocal, justificativaDivergencia,
                new ComprovanteEntregaService.DadosDaEntrega(fotoPorta, tipoRecebedorId, nomeRecebedor, avarias, observacaoAvaria,
                        lerCorrecoes(correcoes)),
                usuario.userId());
        return ResponseEntity.status(resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK).body(ComprovanteEntregaResponse.de(resultado.comprovante()));
    }

    /** V51: corrigir a entrega finalizada (ate 24 h), com motivo; campos ausentes ficam como estao. */
    @PostMapping(value = "/correcao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComprovanteEntregaResponse corrigir(
            @PathVariable UUID entregaId,
            @RequestParam(value = "fotoPorta", required = false) MultipartFile fotoPorta,
            @RequestParam(value = "fotoDocumento", required = false) MultipartFile fotoDocumento,
            @RequestParam(value = "tipoRecebedorId", required = false) UUID tipoRecebedorId,
            @RequestParam(value = "nomeRecebedor", required = false) String nomeRecebedor,
            @RequestParam(value = "corrigirAvarias", defaultValue = "false") boolean corrigirAvarias,
            @RequestParam(value = "avarias", required = false) List<MultipartFile> avarias,
            @RequestParam(value = "observacaoAvaria", required = false) String observacaoAvaria,
            @RequestParam(value = "motivoId", required = false) UUID motivoId,
            @RequestParam(value = "complemento", required = false) String complemento,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ComprovanteEntregaResponse.de(comprovanteEntregaService.corrigir(entregaId, usuario.userId(),
                new ComprovanteEntregaService.CorrecaoDaEntrega(fotoPorta, fotoDocumento, tipoRecebedorId, nomeRecebedor, corrigirAvarias,
                        avarias == null ? List.of() : avarias, observacaoAvaria, motivoId, complemento)));
    }

    private List<CorrecaoInformada> lerCorrecoes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LISTA_DE_CORRECOES);
        } catch (JsonProcessingException e) {
            throw new RegraNegocioException("Correcoes da entrega em formato invalido.");
        }
    }
}
