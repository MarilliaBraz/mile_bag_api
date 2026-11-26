package com.marillia.milebag.business.painelgestao.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.painelgestao.model.BaseCadastrada;
import com.marillia.milebag.business.painelgestao.model.ConfigPainelGestao;
import com.marillia.milebag.business.painelgestao.model.MalaAberta;
import com.marillia.milebag.business.painelgestao.model.MalaEntregue;
import com.marillia.milebag.business.painelgestao.model.MotoristaPeriodo;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoConfig;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoResponse;
import com.marillia.milebag.business.painelgestao.model.ViagemAtiva;
import com.marillia.milebag.business.painelgestao.model.ViagemPeriodo;
import com.marillia.milebag.business.painelgestao.repository.PainelGestaoConfigRepository;
import com.marillia.milebag.business.painelgestao.repository.PainelGestaoRepository;
import com.marillia.milebag.business.viagem.model.CampoAjusteViagem;
import com.marillia.milebag.business.viagem.service.AjusteViagemService;
import com.marillia.milebag.core.base.LinhaNativa;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Painel de gestao da empresa de entrega (fase 1) e a sua configuracao (V61). */
@Service
public class PainelGestaoService {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final int PRAZO_PADRAO_HORAS = 24;
    static final int HORAS_SEM_ATUALIZACAO_PADRAO = 4;
    static final int PRAZO_MAXIMO_HORAS = 720;
    static final int HORAS_SEM_ATUALIZACAO_MAXIMO = 168;
    static final int DIAS_HISTORICO = 30;
    static final int META_OTD_PADRAO = 95;
    static final int META_OTD_MINIMA = 50;

    private final PainelGestaoRepository painelRepository;
    private final PainelGestaoConfigRepository configRepository;
    private final AjusteViagemService ajusteViagemService;

    public PainelGestaoService(PainelGestaoRepository painelRepository, PainelGestaoConfigRepository configRepository,
            AjusteViagemService ajusteViagemService) {
        this.painelRepository = painelRepository;
        this.configRepository = configRepository;
        this.ajusteViagemService = ajusteViagemService;
    }

    @Transactional(readOnly = true)
    public PainelGestaoResponse painel() {
        ConfigPainelGestao config = configuracao();
        Instant agora = Instant.now();
        Instant inicioHoje = LocalDate.now(BRASILIA).atStartOfDay(BRASILIA).toInstant();
        List<MalaAberta> abertas = painelRepository.malasAbertas(config.prazoEntregaHoras()).stream()
                .map(LinhaNativa::new).map(PainelGestaoService::aberta).toList();
        List<MalaEntregue> entregues = painelRepository
                .malasEntregues(config.prazoEntregaHoras(), inicioHoje.minus(Duration.ofDays(DIAS_HISTORICO))).stream()
                .map(LinhaNativa::new).map(PainelGestaoService::entregue).toList();
        return CalculoPainelGestao.calcular(abertas, entregues, agora, inicioHoje, config.prazoEntregaHoras(), config.horasSemAtualizacao(),
                config.metaOtd());
    }

    /** Fase 2: situacao de cada base (mapa e ranking das piores do dia) e as viagens em andamento. */
    @Transactional(readOnly = true)
    public PainelGeograficoResponse painelGeografico() {
        ConfigPainelGestao config = configuracao();
        Instant agora = Instant.now();
        Instant inicioHoje = LocalDate.now(BRASILIA).atStartOfDay(BRASILIA).toInstant();
        List<BaseCadastrada> bases = painelRepository.bases().stream().map(LinhaNativa::new)
                .map(l -> new BaseCadastrada(l.texto(0), l.texto(1), l.texto(2), l.decimal(3), l.decimal(4))).toList();
        List<MalaAberta> abertas = painelRepository.malasAbertas(config.prazoEntregaHoras()).stream()
                .map(LinhaNativa::new).map(PainelGestaoService::aberta).toList();
        List<MalaEntregue> entreguesHoje = painelRepository.malasEntregues(config.prazoEntregaHoras(), inicioHoje).stream()
                .map(LinhaNativa::new).map(PainelGestaoService::entregue).toList();
        List<ViagemAtiva> viagens = painelRepository.rotasAtivas(CalculoPainelGeografico.MINUTOS_PARADO).stream()
                .map(LinhaNativa::new).map(PainelGestaoService::viagem).toList();
        return CalculoPainelGeografico.calcular(bases, abertas, entreguesHoje, viagens, agora, inicioHoje);
    }

    /**
     * Fase 3: custo por mala (hoje e 30 dias), malas por saida e a situacao dos motoristas. O custo de cada viagem vem do
     * mesmo valor vigente que o fechamento usa (tarifa aplicada e pedagio, com os ajustes da empresa).
     */
    @Transactional(readOnly = true)
    public PainelFrotaResponse painelFrota() {
        ConfigPainelGestao config = configuracao();
        Instant agora = Instant.now();
        Instant inicioHoje = LocalDate.now(BRASILIA).atStartOfDay(BRASILIA).toInstant();
        Instant inicio30Dias = inicioHoje.minus(Duration.ofDays(DIAS_HISTORICO));
        List<ViagemPeriodo> viagens = painelRepository.viagensDoPeriodo(inicio30Dias).stream().map(LinhaNativa::new)
                .map(l -> viagemPeriodo(l, inicio30Dias)).toList();
        List<MotoristaPeriodo> motoristas = painelRepository.motoristas(config.prazoEntregaHoras(), inicio30Dias).stream()
                .map(LinhaNativa::new)
                .map(l -> new MotoristaPeriodo(l.uuid(0), l.texto(1), l.texto(2), l.instante(3), l.logico(4), l.numero(5), l.numero(6),
                        l.numero(7)))
                .toList();
        return CalculoPainelFrota.calcular(viagens, motoristas, agora, inicioHoje, inicio30Dias);
    }

    /** O valor so e resolvido para a viagem encerrada no periodo (a em andamento ainda nao tem custo fechado). */
    private ViagemPeriodo viagemPeriodo(LinhaNativa l, Instant inicio30Dias) {
        UUID trajetoId = l.uuid(0);
        Instant encerradaEm = l.instante(3);
        boolean comCusto = encerradaEm != null && !encerradaEm.isBefore(inicio30Dias);
        BigDecimal pagamento = comCusto
                ? ajusteViagemService.resolverValorVigente(trajetoId, CampoAjusteViagem.TARIFA_APLICADA).orElse(null) : null;
        BigDecimal pedagio = comCusto
                ? ajusteViagemService.resolverValorVigente(trajetoId, CampoAjusteViagem.VALOR_PEDAGIO).orElse(null) : null;
        return new ViagemPeriodo(trajetoId, l.uuid(1), l.instante(2), encerradaEm, l.decimal(4) == null ? 0 : l.decimal(4), l.numero(5),
                l.logico(6), pagamento, pedagio);
    }

    @Transactional(readOnly = true)
    public ConfigPainelGestao configuracao() {
        return configRepository.findFirstByOrderByCriadoEmAsc()
                .map(c -> new ConfigPainelGestao(c.getPrazoEntregaHoras(), c.getHorasSemAtualizacao(), c.getMetaOtd()))
                .orElse(new ConfigPainelGestao(PRAZO_PADRAO_HORAS, HORAS_SEM_ATUALIZACAO_PADRAO, META_OTD_PADRAO));
    }

    @Transactional
    public ConfigPainelGestao salvar(ConfigPainelGestao pedido) {
        int prazo = validar(pedido == null ? null : pedido.prazoEntregaHoras(), PRAZO_MAXIMO_HORAS,
                "O prazo padrao deve ficar entre 1 e " + PRAZO_MAXIMO_HORAS + " horas.");
        int parada = validar(pedido == null ? null : pedido.horasSemAtualizacao(), HORAS_SEM_ATUALIZACAO_MAXIMO,
                "As horas sem atualizacao devem ficar entre 1 e " + HORAS_SEM_ATUALIZACAO_MAXIMO + ".");
        Optional<PainelGestaoConfig> atual = configRepository.findFirstByOrderByCriadoEmAsc();
        // Sem meta no pedido (app antigo), fica a que ja estava.
        Integer metaPedida = pedido.metaOtd() != null ? pedido.metaOtd() : atual.map(PainelGestaoConfig::getMetaOtd).orElse(META_OTD_PADRAO);
        if (metaPedida < META_OTD_MINIMA || metaPedida > 100) {
            throw new RegraNegocioException("A meta de entregas no prazo deve ficar entre " + META_OTD_MINIMA + "% e 100%.");
        }
        int meta = metaPedida;
        PainelGestaoConfig config = atual.orElseGet(() -> new PainelGestaoConfig(prazo, parada, meta));
        config.atualizar(prazo, parada, meta);
        configRepository.save(config);
        return new ConfigPainelGestao(prazo, parada, meta);
    }

    private static int validar(Integer valor, int maximo, String mensagem) {
        if (valor == null || valor < 1 || valor > maximo) {
            throw new RegraNegocioException(mensagem);
        }
        return valor;
    }

    private static MalaAberta aberta(LinhaNativa l) {
        return new MalaAberta(l.uuid(0), l.uuid(1), l.texto(2), l.texto(3), l.texto(4), l.texto(5), l.texto(6), l.instante(7),
                l.instante(8), l.instante(9), l.numero(10), l.texto(11), l.logico(12), l.instante(13));
    }

    private static ViagemAtiva viagem(LinhaNativa l) {
        return new ViagemAtiva(l.uuid(0), l.uuid(1), l.texto(2), l.texto(3), l.texto(4), l.texto(5), l.texto(6), l.instante(7), l.decimal(8),
                l.logico(9), l.numero(10), l.logico(11), l.decimal(12) == null ? 0 : l.decimal(12), l.instante(13), l.decimal(14), l.decimal(15),
                l.numero(16), l.decimal(17));
    }

    private static MalaEntregue entregue(LinhaNativa l) {
        return new MalaEntregue(l.uuid(0), l.uuid(1), l.texto(2), l.texto(3), l.texto(4), l.texto(5), l.instante(6), l.instante(7),
                l.instante(8), l.logico(9), l.logico(10), l.logico(11), l.logico(12), l.logico(13), l.instante(14));
    }
}
