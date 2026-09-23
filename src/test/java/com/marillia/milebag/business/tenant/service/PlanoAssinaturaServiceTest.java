package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.tenant.model.PlanoAssinatura;
import com.marillia.milebag.business.tenant.model.PlanoAssinaturaListItemResponse;
import com.marillia.milebag.business.tenant.repository.HistoricoPlanoTenantRepository;
import com.marillia.milebag.business.tenant.repository.PlanoAssinaturaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Cobre HU16 (US1): CRUD de planos de assinatura. */
@ExtendWith(MockitoExtension.class)
class PlanoAssinaturaServiceTest {

    @Mock
    private PlanoAssinaturaRepository planoAssinaturaRepository;
    @Mock
    private HistoricoPlanoTenantRepository historicoPlanoTenantRepository;

    @InjectMocks
    private PlanoAssinaturaService planoAssinaturaService;

    @Test
    void criarPersisteQuandoLimitesValidos() {
        when(planoAssinaturaRepository.save(any(PlanoAssinatura.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanoAssinatura plano = planoAssinaturaService.criar("Plano Basico", 10, 500);

        assertThat(plano.getNome()).isEqualTo("Plano Basico");
        assertThat(plano.getLimiteMotoristasAtivos()).isEqualTo(10);
        assertThat(plano.getLimiteVolumeMensalBdos()).isEqualTo(500);
    }

    @Test
    void criarRecusaLimiteMotoristasZeroOuNegativo() {
        assertThatThrownBy(() -> planoAssinaturaService.criar("Plano X", 0, 500))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> planoAssinaturaService.criar("Plano X", -1, 500))
                .isInstanceOf(RegraNegocioException.class);
        verify(planoAssinaturaRepository, never()).save(any());
    }

    @Test
    void criarRecusaLimiteBdosZeroOuNegativo() {
        assertThatThrownBy(() -> planoAssinaturaService.criar("Plano X", 10, 0))
                .isInstanceOf(RegraNegocioException.class);
        verify(planoAssinaturaRepository, never()).save(any());
    }

    @Test
    void listarIncluiContagemDeTenantsVigentes() {
        PlanoAssinatura plano = new PlanoAssinatura("Plano Pro", 20, 1000);
        when(planoAssinaturaRepository.findAll()).thenReturn(List.of(plano));
        when(historicoPlanoTenantRepository.countByPlanoIdAndVigenteAteIsNull(plano.getId())).thenReturn(3L);

        List<PlanoAssinaturaListItemResponse> itens = planoAssinaturaService.listar();

        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).tenantsAssociados()).isEqualTo(3L);
    }

    @Test
    void removerLancaConflitoQuandoHaTenantAssociado() {
        UUID id = UUID.randomUUID();
        PlanoAssinatura plano = new PlanoAssinatura("Plano Pro", 20, 1000);
        when(planoAssinaturaRepository.findById(id)).thenReturn(java.util.Optional.of(plano));
        when(historicoPlanoTenantRepository.countByPlanoIdAndVigenteAteIsNull(id)).thenReturn(1L);

        assertThatThrownBy(() -> planoAssinaturaService.remover(id)).isInstanceOf(ConflitoException.class);
        verify(planoAssinaturaRepository, never()).delete(any());
    }

    @Test
    void removerApagaQuandoNenhumTenantAssociado() {
        UUID id = UUID.randomUUID();
        PlanoAssinatura plano = new PlanoAssinatura("Plano Pro", 20, 1000);
        when(planoAssinaturaRepository.findById(id)).thenReturn(java.util.Optional.of(plano));
        when(historicoPlanoTenantRepository.countByPlanoIdAndVigenteAteIsNull(id)).thenReturn(0L);

        planoAssinaturaService.remover(id);

        verify(planoAssinaturaRepository).delete(plano);
    }

    @Test
    void buscarOuFalharLancaQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(planoAssinaturaRepository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> planoAssinaturaService.buscarOuFalhar(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
