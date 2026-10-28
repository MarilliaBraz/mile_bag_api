package com.marillia.milebag.business.acompanhamento.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.acompanhamento.model.AcompanhamentoResponse;
import com.marillia.milebag.business.acompanhamento.model.EtapaAcompanhamento;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService;
import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService.LogotipoArquivo;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.rota.service.RotaService;
import com.marillia.milebag.business.tenant.service.TenantService;
import com.marillia.milebag.business.viagem.model.LinkAcompanhamento;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.service.AcompanhamentoViagem;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Monta a pagina publica "Acompanhe sua bagagem" (HU31) a partir do link da tentativa. Le os outros dominios so
 * pelos services deles (CONVENTIONS.md 2.1). {@link #consultar} e {@link #logotipo} rodam com o
 * {@code TenantContext} do tenant dono do link ja definido por quem chama (AcompanhamentoController): toda leitura
 * alem do proprio link segue sob RLS.
 */
@Service
public class AcompanhamentoService {

    private final AcompanhamentoViagem acompanhamentoViagem;
    private final ViagemService viagemService;
    private final EntregaService entregaService;
    private final BdoService bdoService;
    private final RotaService rotaService;
    private final TenantService tenantService;
    private final IdentidadeVisualTenantService identidadeVisualTenantService;

    public AcompanhamentoService(
            AcompanhamentoViagem acompanhamentoViagem,
            ViagemService viagemService,
            EntregaService entregaService,
            BdoService bdoService,
            RotaService rotaService,
            TenantService tenantService,
            IdentidadeVisualTenantService identidadeVisualTenantService) {
        this.acompanhamentoViagem = acompanhamentoViagem;
        this.viagemService = viagemService;
        this.entregaService = entregaService;
        this.bdoService = bdoService;
        this.rotaService = rotaService;
        this.tenantService = tenantService;
        this.identidadeVisualTenantService = identidadeVisualTenantService;
    }

    public LinkAcompanhamento resolver(UUID codigo) {
        return acompanhamentoViagem.buscarLink(codigo).orElseThrow(AcompanhamentoService::linkInvalido);
    }

    @Transactional(readOnly = true)
    public AcompanhamentoResponse consultar(LinkAcompanhamento link) {
        Trajeto trajeto = viagemService.buscarTrajetoPorId(link.getTrajetoId()).orElseThrow(AcompanhamentoService::linkInvalido);
        Entrega entrega = entregaService.buscarPorId(link.getEntregaId()).orElseThrow(AcompanhamentoService::linkInvalido);
        String bdoNumero = bdoService.buscarPorId(entrega.getBdoId()).map(Bdo::getNumero).orElse(null);

        EtapaAcompanhamento etapa = etapa(trajeto, entrega);
        // RN16: posicao so com a tentativa em andamento (encerrar ja a apaga; aqui e a garantia na leitura).
        AcompanhamentoResponse.Posicao posicao = !etapa.encerrada() && trajeto.temPosicao()
                ? new AcompanhamentoResponse.Posicao(
                        trajeto.getPosicaoLatitude(), trajeto.getPosicaoLongitude(), trajeto.getPosicaoRegistradaEm())
                : null;
        AcompanhamentoResponse.Ponto destino = rotaService.buscarDestino(link.getEntregaId())
                .map(c -> new AcompanhamentoResponse.Ponto(c.latitude(), c.longitude()))
                .orElse(null);
        JanelaReagendamento janela = etapa == EtapaAcompanhamento.NAO_ENTREGUE ? entrega.janelaReagendamento() : null;

        return new AcompanhamentoResponse(
                empresa(link),
                bdoNumero,
                etapa,
                trajeto.getDataInicio(),
                posicao,
                destino,
                janela == null ? null : new AcompanhamentoResponse.Janela(janela.inicio(), janela.fim()));
    }

    @Transactional(readOnly = true)
    public LogotipoArquivo logotipo() {
        return identidadeVisualTenantService.carregarLogotipo();
    }

    /** A ocorrencia vem primeiro: o link de uma tentativa sem sucesso continua "nao entregue" mesmo se outra tentativa entregar. */
    private EtapaAcompanhamento etapa(Trajeto trajeto, Entrega entrega) {
        if (trajeto.encerradoPorOcorrencia()) {
            return EtapaAcompanhamento.NAO_ENTREGUE;
        }
        if (entrega.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO) {
            return EtapaAcompanhamento.ENTREGUE;
        }
        return viagemService.marcoMaisRecenteDoTrajeto(trajeto.getId())
                .map(EtapaAcompanhamento::valueOf)
                .orElse(EtapaAcompanhamento.VIAGEM_INICIADA);
    }

    private AcompanhamentoResponse.Empresa empresa(LinkAcompanhamento link) {
        boolean temLogotipo = identidadeVisualTenantService.buscar().map(i -> i.getLogotipoPath() != null).orElse(false);
        return new AcompanhamentoResponse.Empresa(
                tenantService.buscar(link.getTenantId()).getNome(),
                temLogotipo ? "/api/v1/acompanhamento/" + link.getCodigo() + "/logotipo" : null);
    }

    private static RecursoNaoEncontradoException linkInvalido() {
        return new RecursoNaoEncontradoException("Link de acompanhamento invalido.");
    }
}
