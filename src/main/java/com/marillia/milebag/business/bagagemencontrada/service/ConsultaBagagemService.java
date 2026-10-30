package com.marillia.milebag.business.bagagemencontrada.service;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.model.PaginaBagagens;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.geo.MapaEstatico;

/**
 * Consulta das bagagens encontradas da companhia (HU36, RF29): lista com filtros, planilha CSV e ficha em PDF. Tudo pela
 * RLS: outra companhia nao ve nada daqui (a busca nacional e a {@link BuscaNacionalService}).
 */
@Service
public class ConsultaBagagemService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaBagagemService.class);
    static final int TAMANHO_PADRAO = 50;
    static final int TAMANHO_MAXIMO = 100;
    /** A planilha leva as mais recentes ate este limite: e uma exportacao da busca, nao um backup. */
    static final int LINHAS_MAXIMAS_CSV = 5_000;

    private final BagagemEncontradaRepository repository;
    private final BagagemEncontradaService bagagemService;
    private final TenantRepository tenantRepository;
    private final MapaEstatico mapaEstatico;

    public ConsultaBagagemService(
            BagagemEncontradaRepository repository, BagagemEncontradaService bagagemService, TenantRepository tenantRepository,
            MapaEstatico mapaEstatico) {
        this.repository = repository;
        this.bagagemService = bagagemService;
        this.tenantRepository = tenantRepository;
        this.mapaEstatico = mapaEstatico;
    }

    @Transactional(readOnly = true)
    public PaginaBagagens consultar(ConsultaBagagens consulta, Integer pagina, Integer tamanho) {
        int numero = pagina == null ? 0 : pagina;
        int porPagina = tamanho == null ? TAMANHO_PADRAO : tamanho;
        if (numero < 0 || porPagina < 1 || porPagina > TAMANHO_MAXIMO) {
            throw new RegraNegocioException("pagina deve ser 0 ou mais e tamanho de 1 a " + TAMANHO_MAXIMO + ".");
        }
        Page<BagagemEncontrada> resultado = repository.findAll(consulta.especificacao(), PageRequest.of(numero, porPagina, consulta.ordem()));
        return new PaginaBagagens(resultado.map(bagagemService::resposta).getContent(), resultado.getTotalElements(), numero, porPagina);
    }

    /** A mesma busca em CSV; os nomes de quem registrou repetem e vem do cache da transacao. */
    @Transactional(readOnly = true)
    public Arquivo planilha(ConsultaBagagens consulta) {
        List<BagagemEncontradaResponse> linhas = repository
                .findAll(consulta.especificacao(), PageRequest.of(0, LINHAS_MAXIMAS_CSV, consulta.ordem()))
                .map(bagagemService::resposta)
                .getContent();
        return new Arquivo(
                "bagagens-encontradas-" + LocalDate.now(BagagemEncontradaService.BRASILIA) + ".csv", PlanilhaBagagens.gerar(linhas));
    }

    /** A ficha de uma bagagem registrada; o rascunho ainda nao tem os dados conferidos. */
    @Transactional(readOnly = true)
    public Arquivo ficha(UUID id) {
        BagagemEncontrada bagagem = repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Bagagem nao encontrada."));
        if (bagagem.rascunho()) {
            throw new ConflitoException("Registre a bagagem antes de emitir a ficha.");
        }
        BagagemEncontradaResponse resposta = bagagemService.resposta(bagagem);
        byte[] foto = null;
        try {
            foto = bagagemService.foto(id).conteudo();
        } catch (IllegalStateException e) {
            log.warn("Ficha da bagagem {} sem a foto.", id, e);
        }
        byte[] qr = bagagem.getCodigoSid() == null ? null : bagagemService.qrSid(id);
        BufferedImage mapa = bagagem.temLocalizacao()
                ? mapaEstatico.desenhar(bagagem.getLatitude(), bagagem.getLongitude(), FichaBagagemPdf.ZOOM_MAPA, FichaBagagemPdf.LARGURA_MAPA_PX,
                        FichaBagagemPdf.ALTURA_MAPA_PX).orElse(null)
                : null;
        String companhia = tenantRepository.findById(TenantContext.get())
                .map(t -> t.getCodigoIata() == null ? t.getNome() : t.getNome() + " (" + t.getCodigoIata() + ")")
                .orElse("");
        byte[] pdf = FichaBagagemPdf.gerar(new FichaBagagemPdf.Dados(resposta, companhia, foto, qr, mapa, Instant.now()));
        String identificador = bagagem.getCodigoSid() != null ? bagagem.getCodigoSid() : bagagem.getNumeroEtiqueta();
        return new Arquivo("ficha-" + identificador + ".pdf", pdf);
    }

    public record Arquivo(String nome, byte[] conteudo) {
    }
}
