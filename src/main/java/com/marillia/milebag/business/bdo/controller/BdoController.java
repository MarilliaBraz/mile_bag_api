package com.marillia.milebag.business.bdo.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.service.BdoService;

/** Endpoints de captura e conferencia do BDO (HU01) - ver contracts/bdo-api.md. */
@RestController
@RequestMapping("/api/v1/bdos")
public class BdoController {

    private final BdoService bdoService;

    public BdoController(BdoService bdoService) {
        this.bdoService = bdoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BdoResponse capturar(@RequestParam("imagem") MultipartFile imagem) {
        return bdoService.capturar(imagem);
    }

    @PatchMapping("/{id}/conferencia")
    public BdoResponse confirmarConferencia(@PathVariable UUID id, @RequestBody CamposExtraidosBdo camposExtraidos) {
        return bdoService.confirmarConferencia(id, camposExtraidos);
    }

    /** Descarta uma captura por foto ainda nao confirmada (HU01) - 409 para BDO confirmado ou recebido por e-mail. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartarCaptura(@PathVariable UUID id) {
        bdoService.descartarCaptura(id);
    }

    @GetMapping(params = "possivelDuplicidade=true")
    public List<BdoResponse> verificarDuplicidade(@RequestParam String campoChave) {
        return bdoService.buscarPossivelDuplicidade(campoChave);
    }
}
