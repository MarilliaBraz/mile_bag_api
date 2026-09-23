package com.marillia.milebag.business.entrega.mapper;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.model.BagagemCaronaResumo;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.ItemBdoDisponivelResponse;
import com.marillia.milebag.business.entrega.model.ItemPainelDetalheResponse;
import com.marillia.milebag.business.entrega.model.ItemPainelResponse;

/** Monta os read models de HU19 - ver contracts/painel-api.md e data-model.md. */
@Component
public class PainelEntregasMapper {

    /**
     * {@code marcoMaisRecente}: sempre {@code null} ate HU20 existir - o
     * campo ja esta no contrato (data-model.md), mas o checkpoint de
     * progresso ({@code CheckpointGps} de tipo {@code a_caminho}/{@code
     * chegada}) so passa a ser registrado por aquela HU (tasks.md desta HU,
     * T014: "dependencia cruzada com HU20, nao bloqueante").
     */
    public ItemPainelResponse toItemPainel(
            Entrega entrega, Bdo bdo, List<BagagemCarona> bagagensCarona, Function<UUID, String> etiquetaDoBdo) {
        return new ItemPainelResponse(
                entrega.getId(),
                entrega.getStatus(),
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getEtiquetaBagagem(),
                toBagagensResumo(bagagensCarona, etiquetaDoBdo),
                entrega.getAtualizadoEm(),
                entrega.getStatus() == EntregaStatus.EM_ANDAMENTO,
                null);
    }

    public ItemPainelDetalheResponse toDetalhe(
            Entrega entrega, Bdo bdo, List<BagagemCarona> bagagensCarona, Function<UUID, String> etiquetaDoBdo) {
        return new ItemPainelDetalheResponse(
                entrega.getId(),
                entrega.getStatus(),
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getCompanhiaAerea(),
                bdo.getTelefonePassageiro(),
                bdo.getEtiquetaBagagem(),
                toBagagensResumo(bagagensCarona, etiquetaDoBdo),
                entrega.getAtualizadoEm(),
                entrega.getStatus() == EntregaStatus.EM_ANDAMENTO,
                null);
    }

    public ItemBdoDisponivelResponse toItemDisponivel(Bdo bdo) {
        return new ItemBdoDisponivelResponse(
                bdo.getId(),
                bdo.getOrigem(),
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getEtiquetaBagagem(),
                bdo.getDataCaptura());
    }

    private List<BagagemCaronaResumo> toBagagensResumo(List<BagagemCarona> bagagensCarona, Function<UUID, String> etiquetaDoBdo) {
        return bagagensCarona.stream()
                .map(bc -> new BagagemCaronaResumo(bc.getId(), etiquetaDoBdo.apply(bc.getBdoId())))
                .toList();
    }
}
