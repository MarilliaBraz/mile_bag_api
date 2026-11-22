package com.marillia.milebag.business.entrega.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.entrega.model.BdosDisponiveisResponse;
import com.marillia.milebag.business.entrega.model.ItemPainelDetalheResponse;
import com.marillia.milebag.business.entrega.model.ItemBdoDisponivelResponse;
import com.marillia.milebag.business.entrega.model.PainelEntregasResponse;
import com.marillia.milebag.business.entrega.service.BdosDisponiveisService;
import com.marillia.milebag.business.entrega.service.PainelEntregasService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

import java.util.List;
import com.marillia.milebag.business.entrega.model.EntregaCorrigivelResponse;
/**
 * Tela inicial do motorista (HU19) - ver contracts/painel-api.md. Separado
 * de {@code EntregaController} (HU02): aquele cobre autoatribuicao/listagem
 * genérica, este cobre especificamente o read model do painel (research.md
 * desta HU, decisao 1: endpoint dedicado em vez de generalizar HU02).
 */
@RestController
public class PainelEntregasController {

    private final PainelEntregasService painelEntregasService;
    private final BdosDisponiveisService bdosDisponiveisService;

    public PainelEntregasController(PainelEntregasService painelEntregasService, BdosDisponiveisService bdosDisponiveisService) {
        this.painelEntregasService = painelEntregasService;
        this.bdosDisponiveisService = bdosDisponiveisService;
    }

    @GetMapping("/api/v1/motorista/painel-entregas")
    public PainelEntregasResponse painel(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return painelEntregasService.consultarPainel(usuario.userId());
    }

    @GetMapping("/api/v1/entregas/{entregaId}/detalhe")
    public ItemPainelDetalheResponse detalhe(@PathVariable UUID entregaId, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return painelEntregasService.consultarDetalhe(entregaId, usuario.userId());
    }

    /** V51: entregas finalizadas que o motorista ainda pode corrigir (24 h). */
    @GetMapping("/api/v1/motorista/entregas-corrigiveis")
    public List<EntregaCorrigivelResponse> corrigiveis(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return painelEntregasService.listarCorrigiveis(usuario.userId());
    }

    /** Item 8: BDOs disponiveis que vao para o mesmo endereco desta entrega (sugestao de bagagem carona). */
    @GetMapping("/api/v1/entregas/{entregaId}/sugestoes-carona")
    public List<ItemBdoDisponivelResponse> sugestoesCarona(@PathVariable UUID entregaId, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return bdosDisponiveisService.sugestoesDeCarona(entregaId, usuario.userId());
    }

    @GetMapping("/api/v1/motorista/bdos-disponiveis")
    public BdosDisponiveisResponse bdosDisponiveis(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return bdosDisponiveisService.consultarDisponiveis(usuario.userId());
    }
}
