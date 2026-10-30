package com.marillia.milebag.business.bagagemencontrada.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.model.ConfirmarEtiquetaRequest;
import com.marillia.milebag.business.bagagemencontrada.model.DescreverBagagemRequest;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.service.BagagemEncontradaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/**
 * Bagagens encontradas (HU34, HU35) - ver specs/HU34-bagagem-com-etiqueta/contracts/bagagens-api.md e
 * specs/HU35-bagagem-sem-etiqueta/contracts/bagagens-sem-etiqueta-api.md. Restrito aos perfis da
 * companhia aerea ({@code /api/v1/companhia/**} no SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/companhia/bagagens")
public class BagagemEncontradaController {

    private final BagagemEncontradaService service;

    public BagagemEncontradaController(BagagemEncontradaService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BagagemEncontradaResponse lerEtiqueta(
            @RequestParam("foto") MultipartFile foto,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.lerEtiqueta(foto, latitude, longitude, usuario.userId());
    }

    @PostMapping(value = "/sem-etiqueta", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BagagemEncontradaResponse lerSemEtiqueta(
            @RequestParam("foto") MultipartFile foto,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.lerSemEtiqueta(foto, latitude, longitude, usuario.userId());
    }

    @PutMapping("/{id}/descricao")
    public BagagemEncontradaResponse descrever(
            @PathVariable UUID id, @RequestBody DescreverBagagemRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.registrarSemEtiqueta(id, request, usuario.userId());
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr(@PathVariable UUID id) {
        return service.qrSid(id);
    }

    @PutMapping("/{id}/confirmacao")
    public BagagemEncontradaResponse confirmar(
            @PathVariable UUID id, @RequestBody ConfirmarEtiquetaRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.confirmarEtiqueta(id, request, usuario.userId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        service.descartar(id, usuario.userId());
    }

    @GetMapping
    public List<BagagemEncontradaResponse> listar(
            @RequestParam(defaultValue = "REGISTRADA") SituacaoBagagemEncontrada situacao, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.listar(situacao, usuario.userId());
    }

    @GetMapping("/{id}")
    public BagagemEncontradaResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable UUID id) {
        BagagemEncontradaService.Foto foto = service.foto(id);
        MediaType tipo = switch (foto.extensao()) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok().contentType(tipo).body(foto.conteudo());
    }
}
