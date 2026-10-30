package com.marillia.milebag.business.encaminhamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLocalizacao;
import com.marillia.milebag.business.bagagemencontrada.model.TamanhoBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.TipoVolume;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.bagagemencontrada.service.BagagemEncontradaService;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.encaminhamento.model.BagagemEncaminhada;
import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;
import com.marillia.milebag.business.encaminhamento.model.EncaminharBagagemRequest;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.repository.EncaminhamentoEntregaRepository;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class EncaminhamentoServiceTest {

    @Mock
    private EncaminhamentoEntregaRepository repository;
    @Mock
    private BagagemEncontradaRepository bagagemRepository;
    @Mock
    private BagagemEncontradaService bagagemService;
    @Mock
    private ParceriaEntregaService parceriaService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private BdoService bdoService;
    @Mock
    private ApplicationEventPublisher eventos;

    private EncaminhamentoService service;
    private final Tenant companhia = comId(Tenant.companhiaAerea("exemplo", "Linhas Aéreas Exemplo", "ZZ"));
    private final Tenant empresa = comId(new Tenant("entregas-rapidas", "Entregas Rápidas"));
    private final UUID funcionario = UUID.randomUUID();
    private final UUID bdoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EncaminhamentoService(repository, bagagemRepository, bagagemService, parceriaService, tenantRepository, bdoService, eventos);
        TenantContext.set(companhia.getId());
        lenient().when(tenantRepository.findById(companhia.getId())).thenReturn(Optional.of(companhia));
        lenient().when(tenantRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        lenient().when(parceriaService.saoParceiras(companhia.getId(), empresa.getId())).thenReturn(true);
        lenient().when(repository.saveAndFlush(any(EncaminhamentoEntrega.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    void criaOBdoNaEmpresaEVoltaParaOTenantDaCompanhia() {
        BagagemEncontrada bagagem = comEtiqueta();
        when(bagagemRepository.findById(bagagem.getId())).thenReturn(Optional.of(bagagem));
        AtomicReference<UUID> tenantNoBdo = new AtomicReference<>();
        when(bdoService.criarPorCompanhia(any())).thenAnswer(i -> {
            tenantNoBdo.set(TenantContext.get());
            return bdoId;
        });
        BagagemEncontradaResponse esperada = org.mockito.Mockito.mock(BagagemEncontradaResponse.class);
        when(bagagemService.buscar(bagagem.getId())).thenReturn(esperada);

        BagagemEncontradaResponse resposta = service.encaminhar(bagagem.getId(), new EncaminharBagagemRequest(
                empresa.getId(), " gruzz 12345 ", " SILVA/MARIA ", "Rua das Flores, 100, São Paulo/SP", "+55 11 99999-0000", "maria@exemplo.com"), funcionario);

        assertThat(resposta).isSameAs(esperada);
        assertThat(tenantNoBdo.get()).isEqualTo(empresa.getId());
        assertThat(TenantContext.get()).isEqualTo(companhia.getId());
        verify(bdoService).criarPorCompanhia(new CamposExtraidosBdo(
                "GRUZZ12345", "SILVA/MARIA", "Rua das Flores, 100, São Paulo/SP", "Linhas Aéreas Exemplo (ZZ)", "+55 11 99999-0000", "maria@exemplo.com",
                "0957123456"));
        ArgumentCaptor<EncaminhamentoEntrega> salvo = ArgumentCaptor.forClass(EncaminhamentoEntrega.class);
        verify(repository).saveAndFlush(salvo.capture());
        assertThat(salvo.getValue().getEtapa()).isEqualTo(EtapaEntrega.AGUARDANDO_MOTORISTA);
        assertThat(salvo.getValue().getBdoId()).isEqualTo(bdoId);
        assertThat(salvo.getValue().getCompanhiaId()).isEqualTo(companhia.getId());
        assertThat(salvo.getValue().getEmpresaId()).isEqualTo(empresa.getId());
        verify(eventos).publishEvent(new BagagemEncaminhada(
                salvo.getValue().getId(), "maria@exemplo.com", "SILVA/MARIA", "Linhas Aéreas Exemplo (ZZ)", "Entregas Rápidas", "0957123456"));
    }

    @Test
    void semEtiquetaLevaOCodigoSidESemEmailNaoAvisaOPassageiro() {
        BagagemEncontrada bagagem = semEtiqueta();
        when(bagagemRepository.findById(bagagem.getId())).thenReturn(Optional.of(bagagem));
        when(bdoService.criarPorCompanhia(any())).thenReturn(bdoId);

        service.encaminhar(bagagem.getId(), new EncaminharBagagemRequest(empresa.getId(), "GRUZZ12345", "Maria Silva", "Rua A, 1", null, " "), funcionario);

        ArgumentCaptor<CamposExtraidosBdo> campos = ArgumentCaptor.forClass(CamposExtraidosBdo.class);
        verify(bdoService).criarPorCompanhia(campos.capture());
        assertThat(campos.getValue().etiquetaBagagem()).isEqualTo("SID-GRU-2026-000002");
        assertThat(campos.getValue().emailPassageiro()).isNull();
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void recusaRascunhoJaEncaminhadaEmpresaNaoParceiraECamposInvalidos() {
        BagagemEncontrada rascunho = rascunho();
        BagagemEncontrada bagagem = comEtiqueta();
        when(bagagemRepository.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));
        when(bagagemRepository.findById(bagagem.getId())).thenReturn(Optional.of(bagagem));
        UUID outraEmpresa = UUID.randomUUID();

        assertThatThrownBy(() -> service.encaminhar(rascunho.getId(), pedido(empresa.getId(), "GRUZZ12345", "a@b.com"), funcionario))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> service.encaminhar(bagagem.getId(), pedido(outraEmpresa, "GRUZZ12345", "a@b.com"), funcionario))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("parceira");
        assertThatThrownBy(() -> service.encaminhar(bagagem.getId(), pedido(empresa.getId(), "GRU-ZZ/1", "a@b.com"), funcionario))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.encaminhar(bagagem.getId(), pedido(empresa.getId(), "GRUZZ12345", "sem-arroba"), funcionario))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.encaminhar(bagagem.getId(), new EncaminharBagagemRequest(empresa.getId(), "GRUZZ12345", " ", "Rua A", null, null), funcionario))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.encaminhar(UUID.randomUUID(), pedido(empresa.getId(), "GRUZZ12345", null), funcionario))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        when(repository.findByBagagemIdAndCompanhiaIdAndEtapaNot(bagagem.getId(), companhia.getId(), EtapaEntrega.CANCELADO))
                .thenReturn(Optional.of(encaminhamento(bagagem)));
        assertThatThrownBy(() -> service.encaminhar(bagagem.getId(), pedido(empresa.getId(), "GRUZZ12345", null), funcionario))
                .isInstanceOf(ConflitoException.class);
        verifyNoInteractions(bdoService, eventos);
    }

    @Test
    void falhaAoCriarOBdoDevolveOTenantDaCompanhia() {
        BagagemEncontrada bagagem = comEtiqueta();
        when(bagagemRepository.findById(bagagem.getId())).thenReturn(Optional.of(bagagem));
        when(bdoService.criarPorCompanhia(any())).thenThrow(new IllegalStateException("banco fora"));

        assertThatThrownBy(() -> service.encaminhar(bagagem.getId(), pedido(empresa.getId(), "GRUZZ12345", null), funcionario))
                .isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.get()).isEqualTo(companhia.getId());
    }

    @Test
    void cancelaAguardandoMotoristaApagandoOBdoNaEmpresa() {
        BagagemEncontrada bagagem = comEtiqueta();
        EncaminhamentoEntrega ativo = encaminhamento(bagagem);
        when(bagagemRepository.findById(bagagem.getId())).thenReturn(Optional.of(bagagem));
        when(repository.findByBagagemIdAndCompanhiaIdAndEtapaNot(bagagem.getId(), companhia.getId(), EtapaEntrega.CANCELADO)).thenReturn(Optional.of(ativo));
        AtomicReference<UUID> tenantNaRemocao = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(i -> {
            tenantNaRemocao.set(TenantContext.get());
            return null;
        }).when(bdoService).removerCriadoPorCompanhia(bdoId);

        service.cancelar(bagagem.getId(), funcionario);

        assertThat(tenantNaRemocao.get()).isEqualTo(empresa.getId());
        assertThat(TenantContext.get()).isEqualTo(companhia.getId());
        assertThat(ativo.getEtapa()).isEqualTo(EtapaEntrega.CANCELADO);
        assertThat(ativo.getBdoId()).isNull();
        assertThat(ativo.getCanceladoPor()).isEqualTo(funcionario);
        verify(repository).save(ativo);
    }

    @Test
    void naoCancelaDepoisQueUmMotoristaAssumiu() {
        BagagemEncontrada bagagem = comEtiqueta();
        EncaminhamentoEntrega ativo = encaminhamento(bagagem);
        ReflectionTestUtils.setField(ativo, "etapa", EtapaEntrega.MOTORISTA_DESIGNADO);
        when(bagagemRepository.findById(bagagem.getId())).thenReturn(Optional.of(bagagem));
        when(repository.findByBagagemIdAndCompanhiaIdAndEtapaNot(bagagem.getId(), companhia.getId(), EtapaEntrega.CANCELADO)).thenReturn(Optional.of(ativo));

        assertThatThrownBy(() -> service.cancelar(bagagem.getId(), funcionario)).isInstanceOf(ConflitoException.class);
        verifyNoInteractions(bdoService);
        verify(repository, never()).save(eq(ativo));
    }

    private EncaminharBagagemRequest pedido(UUID empresaId, String processo, String email) {
        return new EncaminharBagagemRequest(empresaId, processo, "SILVA/MARIA", "Rua das Flores, 100", null, email);
    }

    private EncaminhamentoEntrega encaminhamento(BagagemEncontrada bagagem) {
        return new EncaminhamentoEntrega(
                bagagem.getId(), companhia.getId(), empresa.getId(), bdoId, "GRUZZ12345", "SILVA/MARIA", "Rua das Flores, 100", funcionario, Instant.now());
    }

    private BagagemEncontrada rascunho() {
        BagagemEncontrada b = BagagemEncontrada.rascunhoComEtiqueta(
                funcionario, "tenant/etiqueta.png", LeituraEtiqueta.vazia(null), -23.4356, -46.4731, OrigemLocalizacao.APARELHO, "GRU", 800);
        b.setId(UUID.randomUUID());
        return b;
    }

    private BagagemEncontrada comEtiqueta() {
        BagagemEncontrada b = rascunho();
        b.registrarEtiqueta("0957123456", null, null, null, "SILVA/MARIA", "GRU", null, Instant.now());
        return b;
    }

    private BagagemEncontrada semEtiqueta() {
        BagagemEncontrada b = BagagemEncontrada.rascunhoSemEtiqueta(
                funcionario, "tenant/mala.jpg", CorBagagem.VERMELHA, -23.4356, -46.4731, OrigemLocalizacao.APARELHO, "GRU", 800);
        b.setId(UUID.randomUUID());
        b.registrarDescricao(TipoVolume.MALA_RIGIDA, TamanhoBagagem.M, CorBagagem.VERMELHA, java.util.Set.of(), null, null, "GRU", null,
                "SID-GRU-2026-000002", Instant.now());
        return b;
    }

    private static Tenant comId(Tenant tenant) {
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        return tenant;
    }
}
