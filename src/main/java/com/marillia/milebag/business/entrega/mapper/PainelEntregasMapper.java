package com.marillia.milebag.business.entrega.mapper;

import java.time.Instant;
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

    /** {@code marcoMaisRecente}: resolvido por {@code ViagemService.marcoMaisRecente} (HU20, FR-006) - nunca calculado aqui. */
    public ItemPainelResponse toItemPainel(
            Entrega entrega, Bdo bdo, List<BagagemCarona> bagagensCarona, Function<UUID, Bdo> bdoDe, String marcoMaisRecente,
            boolean retiradaRegistrada) {
        return new ItemPainelResponse(
                entrega.getId(),
                entrega.getStatus(),
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getEtiquetaBagagem(),
                toBagagensResumo(bagagensCarona, bdoDe),
                entrega.getAtualizadoEm(),
                entrega.getStatus() == EntregaStatus.EM_ANDAMENTO,
                marcoMaisRecente,
                entrega.janelaReagendamento(),
                retiradaRegistrada,
                bdo.getQuantidadeMalas());
    }

    public ItemPainelDetalheResponse toDetalhe(
            Entrega entrega, Bdo bdo, List<BagagemCarona> bagagensCarona, Function<UUID, Bdo> bdoDe, String marcoMaisRecente,
            boolean motivoDispensaObrigatorio, boolean retiradaRegistrada, Instant corrigivelAte) {
        return new ItemPainelDetalheResponse(
                entrega.getId(),
                entrega.getStatus(),
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getCompanhiaAerea(),
                bdo.getTelefonePassageiro(),
                bdo.getEtiquetaBagagem(),
                toBagagensResumo(bagagensCarona, bdoDe),
                entrega.getAtualizadoEm(),
                entrega.getStatus() == EntregaStatus.EM_ANDAMENTO,
                marcoMaisRecente,
                bdo.pendenciasDeContato(),
                entrega.janelaReagendamento(),
                motivoDispensaObrigatorio,
                retiradaRegistrada,
                corrigivelAte,
                bdo.getQuantidadeMalas());
    }

    /** {@code entregaReaberta}: a entrega reaberta deste BDO (RN05), ou nulo se o BDO ainda nao tem entrega. */
    public ItemBdoDisponivelResponse toItemDisponivel(Bdo bdo, Entrega entregaReaberta) {
        return new ItemBdoDisponivelResponse(
                bdo.getId(),
                bdo.getOrigem(),
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getEtiquetaBagagem(),
                bdo.getDataCaptura(),
                bdo.pendenciasDeContato(),
                entregaReaberta != null,
                entregaReaberta == null ? null : entregaReaberta.janelaReagendamento(),
                bdo.getQuantidadeMalas());
    }

    private List<BagagemCaronaResumo> toBagagensResumo(List<BagagemCarona> bagagensCarona, Function<UUID, Bdo> bdoDe) {
        return bagagensCarona.stream()
                .map(bc -> {
                    Bdo carona = bdoDe.apply(bc.getBdoId());
                    return carona == null
                            ? new BagagemCaronaResumo(bc.getId(), null, null, null)
                            : new BagagemCaronaResumo(bc.getId(), carona.getEtiquetaBagagem(), carona.getNumero(), carona.getQuantidadeMalas());
                })
                .toList();
    }
}
