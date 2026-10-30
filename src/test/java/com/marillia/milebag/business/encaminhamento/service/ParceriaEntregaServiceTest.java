package com.marillia.milebag.business.encaminhamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.marillia.milebag.business.encaminhamento.model.ParceriaEntrega;
import com.marillia.milebag.business.encaminhamento.repository.ParceriaEntregaRepository;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.model.TipoTenant;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class ParceriaEntregaServiceTest {

    @Mock
    private ParceriaEntregaRepository repository;
    @Mock
    private TenantRepository tenantRepository;

    private ParceriaEntregaService service;
    private final Tenant companhia = comId(Tenant.companhiaAerea("exemplo", "Linhas Aéreas Exemplo", "ZZ"));
    private final Tenant outraCompanhia = comId(Tenant.companhiaAerea("outra", "Outra Linhas Aéreas", "YY"));
    private final Tenant rapidas = comId(new Tenant("entregas-rapidas", "Entregas Rápidas"));
    private final Tenant levaETraz = comId(new Tenant("leva-e-traz", "Leva e Traz"));

    @BeforeEach
    void setUp() {
        service = new ParceriaEntregaService(repository, tenantRepository);
        for (Tenant t : List.of(companhia, outraCompanhia, rapidas, levaETraz)) {
            lenient().when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));
        }
        lenient().when(tenantRepository.findByTipoOrderByNomeAsc(TipoTenant.EMPRESA_ENTREGA)).thenReturn(List.of(rapidas, levaETraz));
    }

    @Test
    void listaTodasAsEmpresasMarcandoAsParceiras() {
        when(repository.findByCompanhiaId(companhia.getId())).thenReturn(List.of(new ParceriaEntrega(companhia.getId(), levaETraz.getId())));

        assertThat(service.listarParaCompanhia(companhia.getId())).containsExactly(
                new ParceriaEntregaService.EmpresaParceira(rapidas.getId(), "Entregas Rápidas", "entregas-rapidas", false),
                new ParceriaEntregaService.EmpresaParceira(levaETraz.getId(), "Leva e Traz", "leva-e-traz", true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void definirTrocaOConjuntoInteiro() {
        service.definir(companhia.getId(), List.of(rapidas.getId(), rapidas.getId()));

        verify(repository).deleteByCompanhiaId(companhia.getId());
        ArgumentCaptor<List<ParceriaEntrega>> novas = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(novas.capture());
        assertThat(novas.getValue()).singleElement().satisfies(p -> {
            assertThat(p.getCompanhiaId()).isEqualTo(companhia.getId());
            assertThat(p.getEmpresaId()).isEqualTo(rapidas.getId());
        });
    }

    @Test
    void recusaQuemNaoEEmpresaDeEntregaECompanhiaInexistente() {
        assertThatThrownBy(() -> service.definir(companhia.getId(), List.of(outraCompanhia.getId()))).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.definir(companhia.getId(), List.of(UUID.randomUUID()))).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.definir(rapidas.getId(), List.of())).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void aCompanhiaVeSoAsProprias() {
        when(repository.findByCompanhiaId(companhia.getId())).thenReturn(List.of(new ParceriaEntrega(companhia.getId(), rapidas.getId())));
        when(tenantRepository.findAllById(any())).thenReturn(List.of(rapidas));

        assertThat(service.parceirasDa(companhia.getId()))
                .containsExactly(new ParceriaEntregaService.EmpresaEntrega(rapidas.getId(), "Entregas Rápidas"));
        assertThat(service.saoParceiras(companhia.getId(), null)).isFalse();
    }

    private static Tenant comId(Tenant tenant) {
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        return tenant;
    }
}
