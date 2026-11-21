package com.marillia.milebag.business.retirada.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.correcao.model.EtapaCorrecao;
import com.marillia.milebag.business.correcao.model.MomentoCorrecao;
import com.marillia.milebag.business.correcao.service.CorrecaoEntregaService;
import com.marillia.milebag.business.correcao.service.DescricaoCorrecao;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.fotoentrega.model.TipoFotoEntrega;
import com.marillia.milebag.business.fotoentrega.service.FotoEntregaService;
import com.marillia.milebag.business.retirada.model.RetiradaBagagem;
import com.marillia.milebag.business.retirada.model.RetiradaBagagemResponse;
import com.marillia.milebag.business.retirada.repository.RetiradaBagagemRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Retirada da bagagem no aeroporto (V48, itens 4 e 5): primeira etapa depois de assumir. Foto obrigatória, posição
 * registrada onde o motorista estiver (sem bloquear) e até 5 fotos de avaria. A viagem só inicia com a retirada
 * pendente do motorista ({@link #vincularAoTrajeto}). Funciona sem internet (idLocal, como o comprovante).
 * O passageiro não é avisado: a primeira mensagem dele continua sendo o início da viagem.
 */
@Service
public class RetiradaBagagemService {

    private static final Logger log = LoggerFactory.getLogger(RetiradaBagagemService.class);

    private final RetiradaBagagemRepository retiradaBagagemRepository;
    private final EntregaService entregaService;
    private final FotoEntregaService fotoEntregaService;
    private final CorrecaoEntregaService correcaoEntregaService;
    /** Mesma data de corte do comprovante (R): ate la, viagens antigas da fila offline iniciam sem retirada. */
    private final Instant transicaoAte;

    public RetiradaBagagemService(
            RetiradaBagagemRepository retiradaBagagemRepository,
            EntregaService entregaService,
            FotoEntregaService fotoEntregaService,
            CorrecaoEntregaService correcaoEntregaService,
            @Value("${milebag.comprovante.formato-antigo-aceito-ate:2026-12-01T03:00:00Z}") Instant transicaoAte) {
        this.retiradaBagagemRepository = retiradaBagagemRepository;
        this.entregaService = entregaService;
        this.fotoEntregaService = fotoEntregaService;
        this.correcaoEntregaService = correcaoEntregaService;
        this.transicaoAte = transicaoAte;
    }

    @Transactional
    public RetiradaBagagemResponse registrar(
            UUID entregaId, UUID motoristaId, MultipartFile foto, List<MultipartFile> avarias, Double latitude, Double longitude,
            String dataHoraTexto, String idLocal, String observacaoAvaria) {
        Entrega entrega = entregaService.buscarPorId(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
        EntregaService.exigirNaoCancelada(entrega);
        boolean temIdLocal = idLocal != null && !idLocal.isBlank();
        if (temIdLocal) {
            Optional<RetiradaBagagem> existente = retiradaBagagemRepository.findByEntregaIdAndIdLocal(entregaId, idLocal);
            if (existente.isPresent() && existente.get().getMotoristaId().equals(motoristaId)) {
                return RetiradaBagagemResponse.de(existente.get());
            }
        }
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new ConflitoException("So o motorista responsavel pode registrar a retirada desta bagagem.");
        }
        if (entrega.getStatus() != EntregaStatus.ATRIBUIDA) {
            throw new ConflitoException("A retirada so pode ser registrada antes de iniciar a viagem.");
        }
        if (retiradaBagagemRepository.findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motoristaId).isPresent()) {
            throw new ConflitoException("A retirada desta bagagem ja foi registrada.");
        }
        if (foto == null || foto.isEmpty()) {
            throw new RegraNegocioException("A foto da bagagem na retirada e obrigatoria.");
        }
        List<MultipartFile> fotosAvaria = FotoEntregaService.avariasValidas(avarias);
        String observacao = FotoEntregaService.observacaoAvaria(observacaoAvaria);
        Instant dataHora = dataHora(dataHoraTexto);
        boolean comPosicao = latitude != null && longitude != null;

        RetiradaBagagem retirada;
        try {
            // saveAndFlush: o indice de "uma pendente por motorista" (V48) dispara aqui, e nao so no commit.
            retirada = retiradaBagagemRepository.saveAndFlush(new RetiradaBagagem(
                    entregaId, motoristaId, comPosicao ? latitude : null, comPosicao ? longitude : null, observacao, dataHora,
                    temIdLocal, temIdLocal ? idLocal : null));
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("A retirada desta bagagem ja foi registrada.");
        }
        fotoEntregaService.salvarDaRetirada(entregaId, retirada.getId(), TipoFotoEntrega.RETIRADA, foto, dataHora);
        fotosAvaria.forEach(avaria -> fotoEntregaService.salvarDaRetirada(entregaId, retirada.getId(), TipoFotoEntrega.AVARIA_RETIRADA, avaria, dataHora));

        log.info("Retirada {} da entrega {} registrada pelo motorista {} ({} foto(s) de avaria).",
                retirada.getId(), entregaId, motoristaId, fotosAvaria.size());
        return RetiradaBagagemResponse.de(retirada);
    }

    /**
     * Corrigir a retirada antes de iniciar a viagem (V51): foto nova e/ou avarias novas, sempre com motivo. As fotos
     * antigas ficam guardadas como substituidas e cada mudanca vira um registro de correcao (antes, depois, motivo).
     * Precisa de internet. {@code corrigirAvarias} troca o conjunto inteiro (vazio = sem avarias).
     */
    @Transactional
    public RetiradaBagagemResponse corrigir(UUID entregaId, UUID motoristaId, MultipartFile foto, boolean corrigirAvarias,
            List<MultipartFile> avarias, String observacaoAvaria, UUID motivoId, String complemento) {
        // Travada: o inicio da viagem (que trava a mesma entrega) e outro envio da correcao esperam esta terminar.
        Entrega entrega = entregaService.travarParaCorrigir(entregaId);
        EntregaService.exigirNaoCancelada(entrega);
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new ConflitoException("So o motorista responsavel pode corrigir a retirada desta bagagem.");
        }
        RetiradaBagagem retirada = retiradaBagagemRepository
                .findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motoristaId)
                .orElseThrow(() -> new ConflitoException("A retirada so pode ser corrigida depois de registrada e antes de iniciar a viagem."));
        boolean trocaFoto = foto != null && !foto.isEmpty();
        if (!trocaFoto && !corrigirAvarias) {
            throw new RegraNegocioException("Informe o que corrigir: a foto da retirada ou as avarias.");
        }
        correcaoEntregaService.exigirMotivo(motivoId, complemento);
        List<MultipartFile> novasAvarias = corrigirAvarias ? FotoEntregaService.avariasValidas(avarias) : List.of();
        String novaObservacao = corrigirAvarias ? FotoEntregaService.observacaoAvaria(observacaoAvaria) : null;

        Instant agora = Instant.now();
        if (trocaFoto) {
            fotoEntregaService.substituir(entregaId, retirada.getId(), null, TipoFotoEntrega.RETIRADA, List.of(foto), agora);
            correcaoEntregaService.registrar(entregaId, EtapaCorrecao.FOTO_RETIRADA, MomentoCorrecao.APOS_REGISTRO, motivoId, complemento,
                    "foto anterior", "foto nova", motoristaId, agora);
        }
        if (corrigirAvarias) {
            String antes = DescricaoCorrecao.avarias(fotoEntregaService.contarAtuais(entregaId, retirada.getId(), null, TipoFotoEntrega.AVARIA_RETIRADA),
                    retirada.getObservacaoAvaria());
            fotoEntregaService.substituir(entregaId, retirada.getId(), null, TipoFotoEntrega.AVARIA_RETIRADA, novasAvarias, agora);
            retirada.corrigirObservacaoAvaria(novaObservacao);
            retiradaBagagemRepository.save(retirada);
            correcaoEntregaService.registrar(entregaId, EtapaCorrecao.AVARIAS_RETIRADA, MomentoCorrecao.APOS_REGISTRO, motivoId, complemento,
                    antes, DescricaoCorrecao.avarias(novasAvarias.size(), novaObservacao), motoristaId, agora);
        }
        log.info("Retirada {} da entrega {} corrigida pelo motorista {}.", retirada.getId(), entregaId, motoristaId);
        return RetiradaBagagemResponse.de(retirada);
    }

    /** Se o motorista já registrou a retirada desta tentativa (a etapa seguinte é iniciar a viagem). */
    @Transactional(readOnly = true)
    public boolean temRetiradaPendente(UUID entregaId, UUID motoristaId) {
        return motoristaId != null
                && retiradaBagagemRepository.findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motoristaId).isPresent();
    }

    /**
     * Chamado ao iniciar a viagem: sem retirada pendente do motorista, a viagem não começa. {@code daFilaOffline}: viagem
     * guardada por uma versão antiga do app antes da retirada existir - aceita sem retirada ate a data de corte.
     */
    @Transactional
    public void vincularAoTrajeto(UUID entregaId, UUID motoristaId, UUID trajetoId, boolean daFilaOffline) {
        Optional<RetiradaBagagem> pendente = retiradaBagagemRepository
                .findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motoristaId);
        if (pendente.isEmpty()) {
            if (daFilaOffline && Instant.now().isBefore(transicaoAte)) {
                log.warn("Viagem da entrega {} vinda da fila offline sem retirada - aceita na transicao (ate {}).", entregaId, transicaoAte);
                return;
            }
            throw new RegraNegocioException("Registre a retirada da bagagem (com a foto) antes de iniciar a viagem.");
        }
        RetiradaBagagem retirada = pendente.get();
        retirada.vincularAoTrajeto(trajetoId);
        retiradaBagagemRepository.save(retirada);
    }

    /**
     * Dispensa ou cancelamento antes da viagem: a bagagem volta, e a retirada pendente deixa de valer (quem assumir de
     * novo - inclusive o mesmo motorista - precisa retirar outra vez).
     */
    @Transactional
    public void descartarPendentes(UUID entregaId) {
        Instant agora = Instant.now();
        retiradaBagagemRepository.findByEntregaIdAndTrajetoIdIsNullAndDescartadaEmIsNull(entregaId).forEach(retirada -> {
            retirada.descartar(agora);
            retiradaBagagemRepository.save(retirada);
        });
    }

    /** Para a rastreabilidade da mala (uso interno). */
    @Transactional(readOnly = true)
    public List<RetiradaBagagem> listarPorEntrega(UUID entregaId) {
        return retiradaBagagemRepository.findByEntregaIdOrderByDataHoraAsc(entregaId);
    }

    private static Instant dataHora(String texto) {
        if (texto == null || texto.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(texto);
        } catch (DateTimeParseException e) {
            throw new RegraNegocioException("A data/hora da retirada e invalida; use o formato ISO 8601.");
        }
    }
}
