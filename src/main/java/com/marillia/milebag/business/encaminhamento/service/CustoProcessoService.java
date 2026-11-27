package com.marillia.milebag.business.encaminhamento.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.encaminhamento.model.CustoProcesso;
import com.marillia.milebag.business.encaminhamento.model.CustoProcessoRequest;
import com.marillia.milebag.business.encaminhamento.model.CustoProcessoResponse;
import com.marillia.milebag.business.encaminhamento.model.TipoCusto;
import com.marillia.milebag.business.encaminhamento.repository.CustoProcessoRepository;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Painel da companhia, fase 3: custos de cada processo (frete, vouchers, reembolso emergencial, indenizacao), lancados
 * pela companhia. A RLS (V65) garante que so a companhia dona ve e lanca; a bagagem tambem precisa ser dela.
 */
@Service
public class CustoProcessoService {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final BigDecimal VALOR_MAXIMO = new BigDecimal("1000000");
    static final int DESCRICAO_MAX = 200;

    private final CustoProcessoRepository repository;
    private final BagagemEncontradaRepository bagagemRepository;

    public CustoProcessoService(CustoProcessoRepository repository, BagagemEncontradaRepository bagagemRepository) {
        this.repository = repository;
        this.bagagemRepository = bagagemRepository;
    }

    @Transactional(readOnly = true)
    public List<CustoProcessoResponse> listar(UUID bagagemId) {
        exigirBagagem(bagagemId);
        return repository.findByBagagemIdOrderByDataAscCriadoEmAsc(bagagemId).stream().map(CustoProcessoResponse::de).toList();
    }

    @Transactional
    public CustoProcessoResponse lancar(UUID bagagemId, CustoProcessoRequest pedido, UUID usuarioId) {
        exigirBagagem(bagagemId);
        if (pedido == null) {
            throw new RegraNegocioException("Informe o custo.");
        }
        TipoCusto tipo = tipo(pedido.tipo());
        BigDecimal valor = pedido.valor();
        if (valor == null || valor.signum() <= 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
            throw new RegraNegocioException("O valor deve ser maior que zero e ate R$ 1.000.000,00.");
        }
        if (valor.scale() > 2) {
            throw new RegraNegocioException("Use no maximo 2 casas decimais no valor.");
        }
        String descricao = pedido.descricao() == null || pedido.descricao().isBlank() ? null : pedido.descricao().strip();
        if (descricao != null && descricao.length() > DESCRICAO_MAX) {
            throw new RegraNegocioException("A descricao pode ter ate " + DESCRICAO_MAX + " caracteres.");
        }
        LocalDate hoje = LocalDate.now(BRASILIA);
        LocalDate data = pedido.data() == null ? hoje : pedido.data();
        if (data.isAfter(hoje)) {
            throw new RegraNegocioException("A data do custo nao pode ser no futuro.");
        }
        return CustoProcessoResponse.de(repository.save(new CustoProcesso(bagagemId, tipo, valor, descricao, data, usuarioId)));
    }

    @Transactional
    public void remover(UUID bagagemId, UUID custoId) {
        CustoProcesso custo = repository.findByIdAndBagagemId(custoId, bagagemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Custo nao encontrado."));
        repository.delete(custo);
    }

    private void exigirBagagem(UUID bagagemId) {
        bagagemRepository.findById(bagagemId).orElseThrow(() -> new RecursoNaoEncontradoException("Bagagem nao encontrada."));
    }

    private static TipoCusto tipo(String valor) {
        try {
            return TipoCusto.valueOf(valor == null ? "" : valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("Tipo de custo invalido.");
        }
    }
}
