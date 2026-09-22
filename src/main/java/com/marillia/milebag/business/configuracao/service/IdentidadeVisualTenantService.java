package com.marillia.milebag.business.configuracao.service;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.configuracao.model.IdentidadeVisualTenant;
import com.marillia.milebag.business.configuracao.repository.IdentidadeVisualTenantRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.storage.ArmazenamentoImagemService;

/**
 * Identidade visual do tenant (HU13) - ver contracts/identidade-visual-api.md.
 *
 * <p>{@link #buscar()} usa {@code findAll().stream().findFirst()} em vez de
 * uma query por tenantId: a policy de RLS (V20) ja restringe as linhas
 * visiveis ao tenant da requisicao, e o indice unico em tenant_id garante no
 * maximo uma linha - mesmo padrao de leitura "linha unica do tenant atual"
 * usado por {@code TabelaTarifaService}/{@code PeriodicidadeFechamentoConfigService}.</p>
 */
@Service
public class IdentidadeVisualTenantService {

    private static final Logger log = LoggerFactory.getLogger(IdentidadeVisualTenantService.class);

    private final IdentidadeVisualTenantRepository identidadeVisualTenantRepository;
    private final ArmazenamentoImagemService armazenamentoImagemService;

    public IdentidadeVisualTenantService(
            IdentidadeVisualTenantRepository identidadeVisualTenantRepository,
            ArmazenamentoImagemService armazenamentoImagemService) {
        this.identidadeVisualTenantRepository = identidadeVisualTenantRepository;
        this.armazenamentoImagemService = armazenamentoImagemService;
    }

    @Transactional(readOnly = true)
    public Optional<IdentidadeVisualTenant> buscar() {
        return identidadeVisualTenantRepository.findAll().stream().findFirst();
    }

    /**
     * Cria ou substitui a identidade visual do tenant autenticado. A
     * validacao de formato do logotipo (via {@link ArmazenamentoImagemService})
     * acontece antes de qualquer escrita - se o arquivo for invalido, a
     * excecao interrompe a transacao e a identidade anterior (logotipo e
     * cabecalho) permanece intacta (Edge Case 1 do spec).
     */
    @Transactional
    public IdentidadeVisualTenant salvar(MultipartFile logotipo, String cabecalho) {
        Optional<IdentidadeVisualTenant> existente = buscar();

        String logotipoPath = existente.map(IdentidadeVisualTenant::getLogotipoPath).orElse(null);
        if (logotipo != null && !logotipo.isEmpty()) {
            logotipoPath = salvarLogotipo(TenantContext.get(), logotipo);
        }

        if (existente.isPresent()) {
            IdentidadeVisualTenant identidade = existente.get();
            identidade.substituir(logotipoPath, cabecalho);
            log.info("Identidade visual do tenant {} atualizada", identidade.getTenantId());
            return identidadeVisualTenantRepository.save(identidade);
        }

        IdentidadeVisualTenant identidade = identidadeVisualTenantRepository.save(new IdentidadeVisualTenant(logotipoPath, cabecalho));
        log.info("Identidade visual do tenant {} criada", identidade.getTenantId());
        return identidade;
    }

    @Transactional(readOnly = true)
    public LogotipoArquivo carregarLogotipo() {
        IdentidadeVisualTenant identidade = buscar()
                .filter(i -> i.getLogotipoPath() != null)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tenant nao possui logotipo configurado."));
        String caminho = identidade.getLogotipoPath();
        try {
            byte[] conteudo = armazenamentoImagemService.carregar(caminho);
            String extensao = caminho.substring(caminho.lastIndexOf('.') + 1).toLowerCase();
            return new LogotipoArquivo(conteudo, extensao);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar o logotipo.", e);
        }
    }

    /** {@code extensao}: usada pelo controller so para escolher o Content-Type da resposta (ex: "png", "jpeg"). */
    public record LogotipoArquivo(byte[] conteudo, String extensao) {
    }

    /**
     * Usado por {@code RelatorioFechamentoService} (HU12) ao montar o PDF -
     * uma unica leitura para os dois elementos aplicados ao layout
     * (cabecalho/logotipo). Sem identidade configurada, retorna a
     * constante {@link ParaRelatorio#PADRAO_NEUTRO}, e o relatorio segue
     * com a aparencia padrao neutra (FR-004), sem erro.
     */
    @Transactional(readOnly = true)
    public ParaRelatorio carregarParaRelatorio() {
        return buscar()
                .map(identidade -> new ParaRelatorio(
                        identidade.getCabecalho(),
                        identidade.getLogotipoPath() != null ? Optional.of(carregarOuFalhar(identidade.getLogotipoPath())) : Optional.<byte[]>empty()))
                .orElse(ParaRelatorio.PADRAO_NEUTRO);
    }

    public record ParaRelatorio(String cabecalho, Optional<byte[]> logotipo) {
        public static final ParaRelatorio PADRAO_NEUTRO = new ParaRelatorio(null, Optional.empty());
    }

    private byte[] carregarOuFalhar(String caminho) {
        try {
            return armazenamentoImagemService.carregar(caminho);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar o logotipo.", e);
        }
    }

    private String salvarLogotipo(UUID tenantId, MultipartFile logotipo) {
        try {
            return armazenamentoImagemService.salvar(tenantId, logotipo);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar o logotipo.", e);
        }
    }
}
