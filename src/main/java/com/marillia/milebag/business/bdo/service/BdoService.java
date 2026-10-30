package com.marillia.milebag.business.bdo.service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.bdo.mapper.BdoMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoEncaminhadoPorCompanhia;
import com.marillia.milebag.business.bdo.model.BdoRecebidoPorEmail;
import com.marillia.milebag.business.bdo.model.BdoOrigem;
import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.storage.ArmazenamentoImagemService;

/**
 * Orquestra a captura do BDO por fotografia (US1 desta HU): upload da
 * imagem, extracao via OCR e persistencia com {@code origem = FOTO_MANUAL}
 * e {@code statusConferencia = pendente} (FR-002/FR-003/FR-006).
 */
@Service
public class BdoService {

    private static final Logger log = LoggerFactory.getLogger(BdoService.class);

    private final BdoRepository bdoRepository;
    private final ArmazenamentoImagemService armazenamentoImagemService;
    private final BdoOcrService bdoOcrService;
    private final BdoMapper bdoMapper;
    private final ApplicationEventPublisher eventos;

    public BdoService(
            BdoRepository bdoRepository,
            ArmazenamentoImagemService armazenamentoImagemService,
            BdoOcrService bdoOcrService,
            BdoMapper bdoMapper,
            ApplicationEventPublisher eventos) {
        this.bdoRepository = bdoRepository;
        this.armazenamentoImagemService = armazenamentoImagemService;
        this.bdoOcrService = bdoOcrService;
        this.bdoMapper = bdoMapper;
        this.eventos = eventos;
    }

    /** Ponto de leitura publico para outros dominios (rota, entrega, entrega.bagagemcarona) - nunca {@code BdoRepository} direto (CONVENTIONS.md 2.1). */
    @Transactional(readOnly = true)
    public Optional<Bdo> buscarPorId(UUID id) {
        return bdoRepository.findById(id);
    }

    /** Volume de BDOs capturados no mes corrente do tenant ambiente (HU16, uso atual contra o limite do plano). */
    @Transactional(readOnly = true)
    public long contarNoMesCorrente() {
        Instant agora = Instant.now();
        Instant inicioDoMes = agora.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return bdoRepository.countByStatusConferenciaAndDataCapturaBetween(StatusConferencia.CONFIRMADO, inicioDoMes, agora);
    }

    @Transactional
    public BdoResponse capturar(MultipartFile imagem, UUID capturadoPor) {
        if (imagem == null || imagem.isEmpty()) {
            throw new RegraNegocioException("A fotografia do BDO e obrigatoria.");
        }

        CamposExtraidosBdo camposExtraidos = bdoOcrService.extrair(imagem);
        UUID tenantId = TenantContext.get();

        Bdo bdo = new Bdo();
        bdo.setOrigem(BdoOrigem.FOTO_MANUAL);
        bdo.setStatusConferencia(StatusConferencia.PENDENTE);
        bdo.setDataCaptura(Instant.now());
        bdo.setCapturadoPor(capturadoPor);
        bdo.setImagemOriginal(salvarImagem(tenantId, imagem));
        aplicarCamposExtraidos(bdo, camposExtraidos);

        Bdo salvo = bdoRepository.save(bdo);
        log.info("BDO {} capturado por fotografia (tenant {})", salvo.getId(), tenantId);
        return bdoMapper.toResponse(salvo);
    }

    /**
     * Segundo caminho de criacao do BDO (RF21, HU02): a partir de um telex
     * WorldTracer ja reconhecido pelo {@code BdoTelexParser} no corpo de um
     * e-mail - nasce direto {@code CONFIRMADO}, sem imagem nem conferencia
     * (RN01/RI01 nao se aplicam a esta via). Pressupoe que o chamador ja
     * definiu o tenant da requisicao em {@link TenantContext} (ver
     * {@code BdoEmailListener}, que nao tem um JWT de onde resolve-lo).
     */
    @Transactional
    public void criarAutomatico(CamposExtraidosBdo camposExtraidos) {
        Bdo bdo = new Bdo();
        bdo.setOrigem(BdoOrigem.EMAIL_AUTOMATICO);
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        bdo.setDataCaptura(Instant.now());
        aplicarCamposExtraidos(bdo, camposExtraidos);

        Bdo salvo = bdoRepository.save(bdo);
        log.info("BDO {} criado automaticamente por e-mail (tenant {})", salvo.getId(), TenantContext.get());
        // HU32: os motoristas sao avisados depois do commit (NotificadorBdoNovo).
        eventos.publishEvent(new BdoRecebidoPorEmail(salvo.getId(), salvo.getNumero()));
    }

    /**
     * Terceiro caminho de criacao do BDO (HU37, RF30): a companhia aerea encaminha uma bagagem encontrada para esta
     * empresa. Nasce {@code CONFIRMADO}, como o do e-mail, e entra na lista de disponiveis. Pressupoe o tenant da
     * empresa em {@link TenantContext} (ver {@code EncaminhamentoService}); grava na hora para o INSERT sair com ele.
     */
    @Transactional
    public UUID criarPorCompanhia(CamposExtraidosBdo campos) {
        Bdo bdo = new Bdo();
        bdo.setOrigem(BdoOrigem.COMPANHIA_AEREA);
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        bdo.setDataCaptura(Instant.now());
        aplicarCamposExtraidos(bdo, campos);

        Bdo salvo = bdoRepository.saveAndFlush(bdo);
        log.info("BDO {} criado pela companhia {} (tenant {})", salvo.getId(), campos.companhiaAerea(), TenantContext.get());
        eventos.publishEvent(new BdoEncaminhadoPorCompanhia(salvo.getId(), salvo.getNumero(), TenantContext.get(), campos.companhiaAerea()));
        return salvo.getId();
    }

    /**
     * Cancelamento do encaminhamento (HU37): o BDO criado pela companhia sai, se ninguem o assumiu. A entrega e a
     * bagagem carona apontam para o BDO, entao a chave estrangeira tambem recusa numa corrida com o motorista.
     * Pressupoe o tenant da empresa em {@link TenantContext}.
     */
    @Transactional
    public void removerCriadoPorCompanhia(UUID id) {
        Bdo bdo = bdoRepository.findById(id)
                .filter(b -> b.getOrigem() == BdoOrigem.COMPANHIA_AEREA)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
        try {
            bdoRepository.delete(bdo);
            bdoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("A entrega desta bagagem ja tem motorista.");
        }
        log.info("BDO {} da companhia {} removido no cancelamento (tenant {})", id, bdo.getCompanhiaAerea(), TenantContext.get());
    }

    @Transactional
    public BdoResponse confirmarConferencia(UUID id, CamposExtraidosBdo camposConfirmados) {
        Bdo bdo = buscarOuFalhar(id);
        if (bdo.getStatusConferencia() == StatusConferencia.CONFIRMADO) {
            throw new ConflitoException("Este BDO ja foi confirmado anteriormente.");
        }

        validarCamposObrigatorios(camposConfirmados);
        aplicarCamposExtraidos(bdo, camposConfirmados);
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);

        Bdo salvo = bdoRepository.save(bdo);
        log.info("BDO {} confirmado na conferencia", salvo.getId());
        return bdoMapper.toResponse(salvo);
    }

    /**
     * O motorista desistiu da captura na tela de conferencia ("Descartar" ou
     * nova foto): o registro e a imagem saem. So vale para foto ainda nao
     * confirmada - BDO confirmado ou recebido por e-mail nunca e apagado.
     */
    @Transactional
    public void descartarCaptura(UUID id) {
        Bdo bdo = buscarOuFalhar(id);
        if (bdo.getOrigem() != BdoOrigem.FOTO_MANUAL || bdo.getStatusConferencia() != StatusConferencia.PENDENTE) {
            throw new ConflitoException("So uma captura por foto ainda nao confirmada pode ser descartada.");
        }
        excluirComImagem(bdo);
        log.info("Captura de BDO {} descartada na conferencia", id);
    }

    /**
     * Fotos do motorista que ainda esperam conferencia (HU01/HU08). A captura
     * feita sem internet so vira BDO na sincronizacao, depois que o motorista
     * saiu da tela de captura: e por esta lista que ele chega a conferencia.
     */
    @Transactional(readOnly = true)
    public List<BdoResponse> listarPendentesDeConferencia(UUID motoristaId) {
        return bdoRepository.findByCapturadoPorAndOrigemAndStatusConferenciaOrderByDataCapturaAsc(
                        motoristaId, BdoOrigem.FOTO_MANUAL, StatusConferencia.PENDENTE).stream()
                .map(bdoMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BdoResponse> buscarPossivelDuplicidade(String campoChave) {
        return bdoRepository.findByNumero(campoChave).stream()
                .map(bdoMapper::toResponse)
                .toList();
    }

    private Bdo buscarOuFalhar(UUID id) {
        return bdoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
    }

    private void excluirComImagem(Bdo bdo) {
        bdoRepository.delete(bdo);
        // flush antes de apagar o arquivo: se o DELETE falhar, a imagem continua junto do registro.
        bdoRepository.flush();
        if (bdo.getImagemOriginal() == null) {
            return;
        }
        try {
            armazenamentoImagemService.remover(bdo.getImagemOriginal());
        } catch (IOException e) {
            log.warn("BDO {} excluido, mas a imagem {} nao foi removida do disco.", bdo.getId(), bdo.getImagemOriginal(), e);
        }
    }

    private String salvarImagem(UUID tenantId, MultipartFile imagem) {
        try {
            return armazenamentoImagemService.salvar(tenantId, imagem);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar a imagem original do BDO.", e);
        }
    }

    private void aplicarCamposExtraidos(Bdo bdo, CamposExtraidosBdo campos) {
        bdo.setNumero(campos.numero());
        bdo.setPassageiroNome(campos.passageiroNome());
        bdo.setEnderecoEntrega(campos.enderecoEntrega());
        bdo.setCompanhiaAerea(campos.companhiaAerea());
        bdo.setTelefonePassageiro(campos.telefonePassageiro());
        bdo.setEmailPassageiro(campos.emailPassageiro());
        bdo.setEtiquetaBagagem(campos.etiquetaBagagem());
    }

    /**
     * FR-005/T014: exige na confirmacao os campos minimos para viabilizar a
     * entrega - numero do documento, passageiro e endereco. Os demais
     * (voo/cia aerea, contato, etiqueta) permanecem opcionais mesmo apos a
     * confirmacao, pois nem todo BDO real os preenche.
     */
    private void validarCamposObrigatorios(CamposExtraidosBdo campos) {
        List<String> ausentes = new ArrayList<>();
        if (vazio(campos.numero())) {
            ausentes.add("numero");
        }
        if (vazio(campos.passageiroNome())) {
            ausentes.add("passageiroNome");
        }
        if (vazio(campos.enderecoEntrega())) {
            ausentes.add("enderecoEntrega");
        }
        if (!ausentes.isEmpty()) {
            throw new RegraNegocioException("Campos obrigatorios ausentes: " + String.join(", ", ausentes));
        }
    }

    private boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
