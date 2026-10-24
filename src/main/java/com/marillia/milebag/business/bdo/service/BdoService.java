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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.bdo.mapper.BdoMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
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

    public BdoService(
            BdoRepository bdoRepository,
            ArmazenamentoImagemService armazenamentoImagemService,
            BdoOcrService bdoOcrService,
            BdoMapper bdoMapper) {
        this.bdoRepository = bdoRepository;
        this.armazenamentoImagemService = armazenamentoImagemService;
        this.bdoOcrService = bdoOcrService;
        this.bdoMapper = bdoMapper;
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
    public BdoResponse capturar(MultipartFile imagem) {
        if (imagem == null || imagem.isEmpty()) {
            throw new RegraNegocioException("A fotografia do BDO e obrigatoria.");
        }

        CamposExtraidosBdo camposExtraidos = bdoOcrService.extrair(imagem);
        UUID tenantId = TenantContext.get();

        Bdo bdo = new Bdo();
        bdo.setOrigem(BdoOrigem.FOTO_MANUAL);
        bdo.setStatusConferencia(StatusConferencia.PENDENTE);
        bdo.setDataCaptura(Instant.now());
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
