package com.marillia.milebag.business.configuracao.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.configuracao.model.AgendarPeriodicidadeRequest;
import com.marillia.milebag.business.configuracao.model.AtualizarAtivoRequest;
import com.marillia.milebag.business.configuracao.model.CampoObrigatorioRequest;
import com.marillia.milebag.business.configuracao.model.CampoObrigatorioResponse;
import com.marillia.milebag.business.configuracao.model.CodigoNomeRequest;
import com.marillia.milebag.business.configuracao.model.CodigoNomeResponse;
import com.marillia.milebag.business.configuracao.model.CriarMotivoOcorrenciaRequest;
import com.marillia.milebag.business.configuracao.model.MotivoOcorrenciaResponse;
import com.marillia.milebag.business.configuracao.model.PeriodicidadeAtualResponse;
import com.marillia.milebag.business.configuracao.service.BaseAeroportuariaService;
import com.marillia.milebag.business.configuracao.service.CampoBaixaConfigService;
import com.marillia.milebag.business.configuracao.service.CompanhiaAereaAtendidaService;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.configuracao.service.PeriodicidadeFechamentoConfigService;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * As 5 categorias de configuracao do tenant (HU11) - ver
 * contracts/configuracoes-tenant-api.md. Escrita restrita ao perfil
 * administrador do tenant; leitura de motivos de ocorrencia e campos de
 * baixa tambem liberada ao motorista (SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/configuracoes")
public class ConfiguracaoTenantController {

    private final MotivoOcorrenciaService motivoOcorrenciaService;
    private final CampoBaixaConfigService campoBaixaConfigService;
    private final BaseAeroportuariaService baseAeroportuariaService;
    private final CompanhiaAereaAtendidaService companhiaAereaAtendidaService;
    private final PeriodicidadeFechamentoConfigService periodicidadeFechamentoConfigService;

    public ConfiguracaoTenantController(
            MotivoOcorrenciaService motivoOcorrenciaService,
            CampoBaixaConfigService campoBaixaConfigService,
            BaseAeroportuariaService baseAeroportuariaService,
            CompanhiaAereaAtendidaService companhiaAereaAtendidaService,
            PeriodicidadeFechamentoConfigService periodicidadeFechamentoConfigService) {
        this.motivoOcorrenciaService = motivoOcorrenciaService;
        this.campoBaixaConfigService = campoBaixaConfigService;
        this.baseAeroportuariaService = baseAeroportuariaService;
        this.companhiaAereaAtendidaService = companhiaAereaAtendidaService;
        this.periodicidadeFechamentoConfigService = periodicidadeFechamentoConfigService;
    }

    // --- Motivos de ocorrencia ---

    @GetMapping("/motivos-ocorrencia")
    public List<MotivoOcorrenciaResponse> listarMotivosOcorrencia() {
        return motivoOcorrenciaService.listarAtivos().stream().map(MotivoOcorrenciaResponse::de).toList();
    }

    @PostMapping("/motivos-ocorrencia")
    @ResponseStatus(HttpStatus.CREATED)
    public MotivoOcorrenciaResponse criarMotivoOcorrencia(@RequestBody CriarMotivoOcorrenciaRequest request) {
        return MotivoOcorrenciaResponse.de(
                motivoOcorrenciaService.criar(request.descricao(), Boolean.TRUE.equals(request.reagendamento())));
    }

    @PatchMapping("/motivos-ocorrencia/{id}")
    public MotivoOcorrenciaResponse atualizarAtivoMotivoOcorrencia(@PathVariable UUID id, @RequestBody AtualizarAtivoRequest request) {
        if (request.ativo() == null) {
            throw new RegraNegocioException("ativo e obrigatorio.");
        }
        return MotivoOcorrenciaResponse.de(motivoOcorrenciaService.atualizarAtivo(id, request.ativo()));
    }

    // --- Campos obrigatorios do formulario de baixa ---

    @GetMapping("/campos-baixa")
    public List<CampoObrigatorioResponse> listarCamposBaixa() {
        return campoBaixaConfigService.listar();
    }

    @PutMapping("/campos-baixa")
    public List<CampoObrigatorioResponse> atualizarCamposBaixa(@RequestBody List<CampoObrigatorioRequest> request) {
        campoBaixaConfigService.atualizar(request);
        return campoBaixaConfigService.listar();
    }

    // --- Bases aeroportuarias ---

    @GetMapping("/bases-aeroportuarias")
    public List<CodigoNomeResponse> listarBasesAeroportuarias() {
        return baseAeroportuariaService.listar().stream().map(CodigoNomeResponse::de).toList();
    }

    @PostMapping("/bases-aeroportuarias")
    @ResponseStatus(HttpStatus.CREATED)
    public CodigoNomeResponse criarBaseAeroportuaria(@RequestBody CodigoNomeRequest request) {
        return CodigoNomeResponse.de(baseAeroportuariaService.criar(request.codigoIata(), request.nome()));
    }

    @DeleteMapping("/bases-aeroportuarias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerBaseAeroportuaria(@PathVariable UUID id) {
        baseAeroportuariaService.remover(id);
    }

    // --- Companhias aereas atendidas ---

    @GetMapping("/companhias-aereas")
    public List<CodigoNomeResponse> listarCompanhiasAereas() {
        return companhiaAereaAtendidaService.listar().stream().map(CodigoNomeResponse::de).toList();
    }

    @PostMapping("/companhias-aereas")
    @ResponseStatus(HttpStatus.CREATED)
    public CodigoNomeResponse criarCompanhiaAerea(@RequestBody CodigoNomeRequest request) {
        return CodigoNomeResponse.de(companhiaAereaAtendidaService.criar(request.codigoIata(), request.nome()));
    }

    @DeleteMapping("/companhias-aereas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerCompanhiaAerea(@PathVariable UUID id) {
        companhiaAereaAtendidaService.remover(id);
    }

    // --- Periodicidade de fechamento ---

    @GetMapping("/periodicidade-fechamento")
    public PeriodicidadeAtualResponse consultarPeriodicidadeFechamento() {
        return periodicidadeFechamentoConfigService.consultarAtual();
    }

    @PostMapping("/periodicidade-fechamento")
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodicidadeAtualResponse agendarPeriodicidadeFechamento(@RequestBody AgendarPeriodicidadeRequest request) {
        periodicidadeFechamentoConfigService.agendarNovaPeriodicidade(request.periodicidade(), request.vigenteDesde());
        return periodicidadeFechamentoConfigService.consultarAtual();
    }
}
