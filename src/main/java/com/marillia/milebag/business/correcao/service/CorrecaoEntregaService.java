package com.marillia.milebag.business.correcao.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.correcao.model.CorrecaoEntrega;
import com.marillia.milebag.business.correcao.model.CorrecaoInformada;
import com.marillia.milebag.business.correcao.model.EtapaCorrecao;
import com.marillia.milebag.business.correcao.model.MomentoCorrecao;
import com.marillia.milebag.business.correcao.repository.CorrecaoEntregaRepository;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.util.TextoLivre;

/**
 * Registro das correcoes da entrega (V51) - auditoria. Quem corrige a retirada ou o comprovante e o proprio dominio
 * (RetiradaBagagemService, ComprovanteEntregaService); aqui fica so o "o que, por que, antes, depois, quem e quando".
 */
@Service
public class CorrecaoEntregaService {

    private static final Logger log = LoggerFactory.getLogger(CorrecaoEntregaService.class);

    /** Mais que isso numa entrega so e erro do aparelho, nao correcao. */
    static final int MAXIMO_CORRECOES_NO_FLUXO = 30;
    private static final int TAMANHO_MAXIMO_COMPLEMENTO = 500;
    private static final int TAMANHO_MAXIMO_VALOR = 300;

    private final CorrecaoEntregaRepository repository;
    private final MotivoOcorrenciaService motivoOcorrenciaService;

    public CorrecaoEntregaService(CorrecaoEntregaRepository repository, MotivoOcorrenciaService motivoOcorrenciaService) {
        this.repository = repository;
        this.motivoOcorrenciaService = motivoOcorrenciaService;
    }

    /**
     * Valida o motivo e o complemento antes de qualquer gravacao. Na correcao feita com internet (retirada, entrega
     * finalizada) o motivo tem de estar ativo na lista da empresa.
     */
    public void exigirMotivo(UUID motivoId, String complemento) {
        if (motivoId == null) {
            throw new RegraNegocioException("Informe o motivo da correcao.");
        }
        motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId, FinalidadeMotivo.CORRECAO);
        TextoLivre.opcional(complemento, TAMANHO_MAXIMO_COMPLEMENTO, "complemento da correcao");
    }

    /** Correcao ja validada por {@link #exigirMotivo} (retirada, entrega finalizada). */
    @Transactional
    public CorrecaoEntrega registrar(UUID entregaId, EtapaCorrecao etapa, MomentoCorrecao momento, UUID motivoId, String complemento,
            String antes, String depois, UUID corrigidoPor, Instant dataHora) {
        return gravar(entregaId, etapa, momento, motivoId, complemento, antes, depois, corrigidoPor, dataHora);
    }

    private CorrecaoEntrega gravar(UUID entregaId, EtapaCorrecao etapa, MomentoCorrecao momento, UUID motivoId, String complemento,
            String antes, String depois, UUID corrigidoPor, Instant dataHora) {
        return repository.save(new CorrecaoEntrega(entregaId, etapa, momento, motivoId,
                TextoLivre.opcional(complemento, TAMANHO_MAXIMO_COMPLEMENTO, "complemento da correcao"),
                limitar(antes), limitar(depois), corrigidoPor, dataHora));
    }

    /**
     * As correcoes feitas no aparelho antes de finalizar, que chegam com o comprovante (inclusive pela fila offline).
     * Sao auditoria: uma correcao invalida (sem etapa, motivo que nao e de correcao) e descartada com aviso no log, sem
     * impedir o encerramento da entrega. O motivo pode ter sido desativado depois - o aparelho usou a lista que tinha.
     */
    @Transactional
    public void registrarDoFluxo(UUID entregaId, UUID corrigidoPor, List<CorrecaoInformada> correcoes, Instant padrao) {
        if (correcoes == null || correcoes.isEmpty()) {
            return;
        }
        List<CorrecaoInformada> consideradas = correcoes.size() > MAXIMO_CORRECOES_NO_FLUXO
                ? correcoes.subList(0, MAXIMO_CORRECOES_NO_FLUXO) : correcoes;
        if (consideradas.size() < correcoes.size()) {
            log.warn("Entrega {}: {} correcoes no fluxo, so as {} primeiras foram registradas.", entregaId, correcoes.size(), MAXIMO_CORRECOES_NO_FLUXO);
        }
        for (CorrecaoInformada correcao : consideradas) {
            if (correcao.etapa() == null || correcao.motivoId() == null) {
                log.warn("Entrega {}: correcao do fluxo sem etapa ou motivo descartada.", entregaId);
                continue;
            }
            try {
                motivoOcorrenciaService.buscarDaFinalidadeOuFalhar(correcao.motivoId(), FinalidadeMotivo.CORRECAO);
                gravar(entregaId, correcao.etapa(), MomentoCorrecao.NO_FLUXO, correcao.motivoId(),
                        limitarComplemento(correcao.complemento()), correcao.antes(), correcao.depois(), corrigidoPor,
                        instante(correcao.dataHora(), padrao));
            } catch (RecursoNaoEncontradoException e) {
                log.warn("Entrega {}: correcao do fluxo com motivo {} que nao e de correcao - descartada.", entregaId, correcao.motivoId());
            }
        }
    }

    private static String limitarComplemento(String complemento) {
        if (complemento == null || complemento.isBlank()) {
            return null;
        }
        String limpo = complemento.strip();
        return limpo.length() > TAMANHO_MAXIMO_COMPLEMENTO ? limpo.substring(0, TAMANHO_MAXIMO_COMPLEMENTO) : limpo;
    }

    @Transactional(readOnly = true)
    public List<CorrecaoEntrega> listarPorEntrega(UUID entregaId) {
        return repository.findByEntregaIdOrderByDataHoraAsc(entregaId);
    }

    @Transactional(readOnly = true)
    public Set<UUID> entregasCorrigidas(Collection<UUID> entregas) {
        return entregas.isEmpty() ? Set.of() : new HashSet<>(repository.entregasCorrigidas(entregas));
    }

    private static Instant instante(String texto, Instant padrao) {
        if (texto == null || texto.isBlank()) {
            return padrao;
        }
        try {
            Instant informado = Instant.parse(texto);
            // Relogio do aparelho adiantado nao vira correcao "no futuro".
            return informado.isAfter(Instant.now()) ? padrao : informado;
        } catch (DateTimeParseException e) {
            return padrao;
        }
    }

    private static String limitar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.strip();
        return limpo.length() > TAMANHO_MAXIMO_VALOR ? limpo.substring(0, TAMANHO_MAXIMO_VALOR) : limpo;
    }
}
