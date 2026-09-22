package com.marillia.milebag.business.pedagio.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.pedagio.model.PracaPedagio;
import com.marillia.milebag.business.pedagio.model.PracaPedagioResponse;
import com.marillia.milebag.business.pedagio.model.PracaPedagioValor;
import com.marillia.milebag.business.pedagio.repository.PracaPedagioRepository;
import com.marillia.milebag.business.pedagio.repository.PracaPedagioValorRepository;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Cadastro e historico versionado de pracas de pedagio (US1 desta HU) -
 * ver contracts/pedagios-api.md.
 */
@Service
public class PracaPedagioService {

    private static final Logger log = LoggerFactory.getLogger(PracaPedagioService.class);
    private static final BigDecimal DOBRO = BigDecimal.valueOf(2);

    private final PracaPedagioRepository pracaPedagioRepository;
    private final PracaPedagioValorRepository pracaPedagioValorRepository;

    public PracaPedagioService(PracaPedagioRepository pracaPedagioRepository, PracaPedagioValorRepository pracaPedagioValorRepository) {
        this.pracaPedagioRepository = pracaPedagioRepository;
        this.pracaPedagioValorRepository = pracaPedagioValorRepository;
    }

    @Transactional
    public PracaPedagioResponse criarPraca(String nome, BigDecimal valor, LocalDate vigenteDesde) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("nome e obrigatorio.");
        }
        validarValor(valor);
        validarVigencia(vigenteDesde);

        PracaPedagio praca = pracaPedagioRepository.save(new PracaPedagio(nome));
        PracaPedagioValor primeiroValor = pracaPedagioValorRepository.save(new PracaPedagioValor(praca.getId(), valor, vigenteDesde));

        log.info("Praca de pedagio {} criada com valor inicial {} (vigente desde {})", praca.getId(), valor, vigenteDesde);
        return PracaPedagioResponse.de(praca, primeiroValor);
    }

    @Transactional
    public PracaPedagioValor adicionarValor(UUID pracaId, BigDecimal valor, LocalDate vigenteDesde) {
        PracaPedagio praca = buscarPracaOuFalhar(pracaId);
        validarValor(valor);
        validarVigencia(vigenteDesde);

        Optional<PracaPedagioValor> maisRecente = pracaPedagioValorRepository.findFirstByPracaPedagioIdOrderByVigenteDesdeDesc(praca.getId());
        if (maisRecente.isPresent() && !vigenteDesde.isAfter(maisRecente.get().getVigenteDesde())) {
            throw new RegraNegocioException(
                    "vigenteDesde deve ser posterior a vigencia do valor mais recente (" + maisRecente.get().getVigenteDesde() + ").");
        }

        PracaPedagioValor novoValor = pracaPedagioValorRepository.save(new PracaPedagioValor(praca.getId(), valor, vigenteDesde));
        log.info("Novo valor {} cadastrado para a praca {} (vigente desde {})", valor, praca.getId(), vigenteDesde);
        return novoValor;
    }

    @Transactional(readOnly = true)
    public List<PracaPedagioResponse> listarPracas() {
        return pracaPedagioRepository.findAll().stream()
                .map(praca -> PracaPedagioResponse.de(praca, valorVigenteEmOuNulo(praca.getId(), LocalDate.now())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PracaPedagioValor> historico(UUID pracaId) {
        buscarPracaOuFalhar(pracaId);
        return pracaPedagioValorRepository.findByPracaPedagioIdOrderByVigenteDesdeDesc(pracaId);
    }

    /** FR-003: usado pelo congelamento de rota (HU04) e pelo fechamento (HU12) - ainda sem chamador nesta HU. */
    @Transactional(readOnly = true)
    public PracaPedagioValor valorVigenteEm(UUID pracaId, LocalDate data) {
        buscarPracaOuFalhar(pracaId);
        return pracaPedagioValorRepository
                .findFirstByPracaPedagioIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(pracaId, data)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Praca sem valor vigente ate a data informada."));
    }

    /** FR-004/RN06: reembolso e sempre o dobro do valor vigente (ida + volta) - ainda sem chamador nesta HU (HU12). */
    @Transactional(readOnly = true)
    public BigDecimal calcularReembolso(UUID pracaId, LocalDate data) {
        return valorVigenteEm(pracaId, data).getValor().multiply(DOBRO);
    }

    private PracaPedagioValor valorVigenteEmOuNulo(UUID pracaId, LocalDate data) {
        return pracaPedagioValorRepository
                .findFirstByPracaPedagioIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(pracaId, data)
                .orElse(null);
    }

    private PracaPedagio buscarPracaOuFalhar(UUID pracaId) {
        return pracaPedagioRepository.findById(pracaId).orElseThrow(() -> new RecursoNaoEncontradoException("Praca de pedagio nao encontrada."));
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null) {
            throw new RegraNegocioException("valor e obrigatorio.");
        }
    }

    private void validarVigencia(LocalDate vigenteDesde) {
        if (vigenteDesde == null) {
            throw new RegraNegocioException("vigenteDesde e obrigatorio.");
        }
    }
}
