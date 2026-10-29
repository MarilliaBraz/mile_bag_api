package com.marillia.milebag.business.bagagemencontrada.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TipoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TipoVolume;
import com.marillia.milebag.core.exception.RegraNegocioException;

import jakarta.persistence.criteria.Predicate;

/**
 * Filtros da consulta de bagagens encontradas (HU36, RF29), ja validados; vazios nao filtram. As datas sao do horario de
 * Brasilia e inclusivas, sobre a data do registro (ou da foto, no rascunho). A companhia vem da RLS.
 */
public record ConsultaBagagens(
        String aeroporto, LocalDate de, LocalDate ate, TipoBagagemEncontrada tipo, TipoVolume tipoVolume, CorBagagem cor,
        SituacaoBagagemEncontrada situacao, String texto) {

    static final int TEXTO_MAXIMO = 60;

    /** Os parametros da query string, como chegam; valor fora das opcoes e erro do usuario (400). */
    public static ConsultaBagagens de(
            String aeroporto, String de, String ate, String tipo, String tipoVolume, String cor, String situacao, String texto) {
        String codigo = vazioComoNulo(aeroporto);
        if (codigo != null && !codigo.matches("[A-Za-z]{3}")) {
            throw new RegraNegocioException("aeroporto deve ser um codigo de 3 letras.");
        }
        LocalDate inicio = data(de, "de");
        LocalDate fim = data(ate, "ate");
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new RegraNegocioException("A data inicial deve ser anterior ou igual a final.");
        }
        String procurado = vazioComoNulo(texto);
        if (procurado != null && procurado.length() > TEXTO_MAXIMO) {
            throw new RegraNegocioException("texto deve ter no maximo " + TEXTO_MAXIMO + " caracteres.");
        }
        SituacaoBagagemEncontrada estado = opcao(SituacaoBagagemEncontrada.class, situacao, "situacao");
        return new ConsultaBagagens(
                codigo == null ? null : codigo.toUpperCase(Locale.ROOT), inicio, fim, opcao(TipoBagagemEncontrada.class, tipo, "tipo"),
                opcao(TipoVolume.class, tipoVolume, "tipoVolume"), opcao(CorBagagem.class, cor, "cor"),
                estado == null ? SituacaoBagagemEncontrada.REGISTRADA : estado, procurado);
    }

    /** A data que conta: a do registro; no rascunho, que ainda nao tem, a da foto. */
    private String campoData() {
        return situacao == SituacaoBagagemEncontrada.RASCUNHO ? "criadoEm" : "registradaEm";
    }

    /** Mais recentes primeiro; o id desempata, para a paginacao nao repetir nem pular registros. */
    public Sort ordem() {
        return Sort.by(Sort.Order.desc(campoData()), Sort.Order.desc("id"));
    }

    public Specification<BagagemEncontrada> especificacao() {
        return (raiz, consulta, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            condicoes.add(cb.equal(raiz.get("situacao"), situacao));
            if (aeroporto != null) {
                condicoes.add(cb.equal(raiz.get("aeroportoCodigo"), aeroporto));
            }
            if (de != null) {
                condicoes.add(cb.greaterThanOrEqualTo(raiz.<Instant>get(campoData()), de.atStartOfDay(BagagemEncontradaService.BRASILIA).toInstant()));
            }
            if (ate != null) {
                condicoes.add(cb.lessThan(raiz.<Instant>get(campoData()), ate.plusDays(1).atStartOfDay(BagagemEncontradaService.BRASILIA).toInstant()));
            }
            if (tipo != null) {
                condicoes.add(cb.equal(raiz.get("tipo"), tipo));
            }
            if (tipoVolume != null) {
                condicoes.add(cb.equal(raiz.get("tipoVolume"), tipoVolume));
            }
            if (cor != null) {
                condicoes.add(cb.equal(raiz.get("cor"), cor));
            }
            if (texto != null) {
                String padrao = "%" + texto.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                condicoes.add(cb.or(
                        cb.like(cb.lower(raiz.get("numeroEtiqueta")), padrao, '\\'),
                        cb.like(cb.lower(raiz.get("lidoNumeroEtiqueta")), padrao, '\\'),
                        cb.like(cb.lower(raiz.get("codigoSid")), padrao, '\\'),
                        cb.like(cb.lower(raiz.get("nomePassageiro")), padrao, '\\')));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static LocalDate data(String valor, String campo) {
        String limpo = vazioComoNulo(valor);
        if (limpo == null) {
            return null;
        }
        try {
            return LocalDate.parse(limpo);
        } catch (DateTimeParseException e) {
            throw new RegraNegocioException(campo + " deve ser uma data no formato AAAA-MM-DD.");
        }
    }

    private static <E extends Enum<E>> E opcao(Class<E> opcoes, String valor, String campo) {
        String limpo = vazioComoNulo(valor);
        if (limpo == null) {
            return null;
        }
        try {
            return Enum.valueOf(opcoes, limpo.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException(campo + " fora das opcoes: " + valor + ".");
        }
    }
}
