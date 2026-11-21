package com.marillia.milebag.business.fotoentrega.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.fotoentrega.model.FotoEntrega;
import com.marillia.milebag.business.fotoentrega.model.TipoFotoEntrega;
import com.marillia.milebag.business.fotoentrega.repository.FotoEntregaRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.storage.ArmazenamentoImagemService;
import com.marillia.milebag.core.util.TextoLivre;

/**
 * Fotos da entrega (V48): grava no armazenamento de imagens (que confere o conteúdo real do arquivo) e registra de onde
 * cada uma veio. Ponto único de escrita e leitura para a retirada, o comprovante e a tela da empresa.
 */
@Service
public class FotoEntregaService {

    private static final Logger log = LoggerFactory.getLogger(FotoEntregaService.class);

    /** I: até 5 fotos de avaria na retirada e outras 5 na entrega. */
    public static final int MAXIMO_FOTOS_AVARIA = 5;
    static final int TAMANHO_MAXIMO_OBSERVACAO = 500;

    private final FotoEntregaRepository fotoEntregaRepository;
    private final ArmazenamentoImagemService armazenamentoImagemService;

    public FotoEntregaService(FotoEntregaRepository fotoEntregaRepository, ArmazenamentoImagemService armazenamentoImagemService) {
        this.fotoEntregaRepository = fotoEntregaRepository;
        this.armazenamentoImagemService = armazenamentoImagemService;
    }

    /** Recusa mais fotos de avaria que o limite, antes de gravar qualquer arquivo. */
    public static List<MultipartFile> avariasValidas(List<MultipartFile> avarias) {
        List<MultipartFile> preenchidas = avarias == null ? List.of() : avarias.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (preenchidas.size() > MAXIMO_FOTOS_AVARIA) {
            throw new RegraNegocioException("Envie no maximo " + MAXIMO_FOTOS_AVARIA + " fotos de avaria.");
        }
        return preenchidas;
    }

    /** A observacao das avarias (retirada e entrega): aparada, vazia vira nulo, com limite. */
    public static String observacaoAvaria(String texto) {
        return TextoLivre.opcional(texto, TAMANHO_MAXIMO_OBSERVACAO, "observacao das avarias");
    }

    @Transactional
    public void salvarDaRetirada(UUID entregaId, UUID retiradaId, TipoFotoEntrega tipo, MultipartFile foto, Instant dataHora) {
        fotoEntregaRepository.save(new FotoEntrega(entregaId, tipo, retiradaId, null, gravar(foto), dataHora));
    }

    @Transactional
    public void salvarDoComprovante(UUID entregaId, UUID comprovanteId, TipoFotoEntrega tipo, MultipartFile foto, Instant dataHora) {
        fotoEntregaRepository.save(new FotoEntrega(entregaId, tipo, null, comprovanteId, gravar(foto), dataHora));
    }

    /**
     * Correcao (V51): as fotos atuais deste tipo da retirada ou do comprovante ficam substituidas (nunca apagadas) e as
     * novas entram no lugar. Lista vazia so retira as atuais (ex.: avarias que nao existiam).
     */
    @Transactional
    public void substituir(UUID entregaId, UUID retiradaId, UUID comprovanteId, TipoFotoEntrega tipo, List<MultipartFile> novas, Instant quando) {
        fotoEntregaRepository.findByEntregaIdOrderByDataHoraAsc(entregaId).stream()
                .filter(foto -> foto.getTipo() == tipo && !foto.substituida())
                .filter(foto -> retiradaId != null ? retiradaId.equals(foto.getRetiradaId()) : comprovanteId.equals(foto.getComprovanteId()))
                .forEach(foto -> {
                    foto.substituir(quando);
                    fotoEntregaRepository.save(foto);
                });
        for (MultipartFile nova : novas) {
            fotoEntregaRepository.save(new FotoEntrega(entregaId, tipo, retiradaId, comprovanteId, gravar(nova), quando));
        }
    }

    /** A foto do BDO assinado que a correcao trocou no comprovante fica guardada aqui, como substituida (V51). */
    @Transactional
    public void guardarBdoAssinadoSubstituido(UUID entregaId, UUID comprovanteId, String caminhoAntigo, Instant tiradaEm, Instant quando) {
        fotoEntregaRepository.save(FotoEntrega.bdoAssinadoSubstituido(entregaId, comprovanteId, caminhoAntigo, tiradaEm, quando));
    }

    /** Quantas fotos atuais (nao substituidas) deste tipo a retirada ou o comprovante tem - para descrever a correcao. */
    @Transactional(readOnly = true)
    public long contarAtuais(UUID entregaId, UUID retiradaId, UUID comprovanteId, TipoFotoEntrega tipo) {
        return fotoEntregaRepository.findByEntregaIdOrderByDataHoraAsc(entregaId).stream()
                .filter(foto -> foto.getTipo() == tipo && !foto.substituida())
                .filter(foto -> retiradaId != null ? retiradaId.equals(foto.getRetiradaId()) : comprovanteId.equals(foto.getComprovanteId()))
                .count();
    }

    @Transactional(readOnly = true)
    public List<FotoEntrega> listarPorEntrega(UUID entregaId) {
        return fotoEntregaRepository.findByEntregaIdOrderByDataHoraAsc(entregaId);
    }

    /** O arquivo de uma foto desta entrega - 404 se a foto for de outra entrega (ou de outra empresa, pela RLS). */
    @Transactional(readOnly = true)
    public Arquivo carregar(UUID entregaId, UUID fotoId) {
        FotoEntrega foto = fotoEntregaRepository.findById(fotoId)
                .filter(encontrada -> encontrada.getEntregaId().equals(entregaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto nao encontrada."));
        return carregarCaminho(foto.getCaminho());
    }

    /** Para fotos guardadas fora desta tabela (a do BDO assinado, no comprovante). */
    public Arquivo carregarCaminho(String caminho) {
        try {
            return new Arquivo(armazenamentoImagemService.carregar(caminho), caminho.substring(caminho.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar a foto da entrega.", e);
        }
    }

    /**
     * Grava o arquivo da foto (tambem usado para a do BDO assinado). Se a transacao em curso for desfeita - divergencia de
     * quilometragem no comprovante, imagem invalida no meio das avarias -, o arquivo e apagado: nao sobra foto sem registro.
     */
    public String gravarArquivo(MultipartFile foto) {
        String caminho;
        try {
            caminho = armazenamentoImagemService.salvar(TenantContext.get(), foto);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar a foto da entrega.", e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        removerSemFalhar(caminho);
                    }
                }
            });
        }
        return caminho;
    }

    private void removerSemFalhar(String caminho) {
        try {
            armazenamentoImagemService.remover(caminho);
        } catch (IOException | RuntimeException e) {
            log.warn("Nao foi possivel apagar a foto {} de uma operacao desfeita.", caminho, e);
        }
    }

    private String gravar(MultipartFile foto) {
        return gravarArquivo(foto);
    }

    public record Arquivo(byte[] conteudo, String extensao) {
    }
}
