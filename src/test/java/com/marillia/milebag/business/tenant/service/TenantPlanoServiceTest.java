package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.tenant.model.HistoricoPlanoTenant;
import com.marillia.milebag.business.tenant.model.PlanoAssinatura;
import com.marillia.milebag.business.tenant.model.PlanoVigenteConsulta;
import com.marillia.milebag.business.tenant.model.TrocaPlanoResultado;
import com.marillia.milebag.business.tenant.repository.HistoricoPlanoTenantRepository;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/** Cobre HU16 (US1): associacao inicial e troca de plano de um tenant. */
@ExtendWith(MockitoExtension.class)
class TenantPlanoServiceTest {

    @Mock
    private HistoricoPlanoTenantRepository historicoPlanoTenantRepository;
    @Mock
    private PlanoAssinaturaService planoAssinaturaService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private BdoService bdoService;

    private TenantPlanoService tenantPlanoService;

    @BeforeEach
    void setUp() {
        tenantPlanoService = new TenantPlanoService(
                historicoPlanoTenantRepository, planoAssinaturaService, tenantRepository, usuarioRepository, bdoService);
        // lenient: nem todo teste chega a salvar (ex.: trocarPlanoLancaQuandoTenantNaoExiste).
        org.mockito.Mockito.lenient()
                .when(historicoPlanoTenantRepository.save(any(HistoricoPlanoTenant.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void associarInicialCriaRegistroSemExcedente() {
        UUID tenantId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();

        tenantPlanoService.associarInicial(tenantId, planoId);

        ArgumentCaptor<HistoricoPlanoTenant> captor = ArgumentCaptor.forClass(HistoricoPlanoTenant.class);
        verify(historicoPlanoTenantRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getPlanoId()).isEqualTo(planoId);
        assertThat(captor.getValue().isUsoExcedeuLimiteNaTroca()).isFalse();
        assertThat(captor.getValue().getVigenteAte()).isNull();
    }

    @Test
    void trocarPlanoLancaQuandoTenantNaoExiste() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> tenantPlanoService.trocarPlano(tenantId, UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(historicoPlanoTenantRepository, never()).save(any());
    }

    @Test
    void trocarPlanoEncerraOVigenteAnteriorECriaONovo() {
        UUID tenantId = UUID.randomUUID();
        UUID planoAntigoId = UUID.randomUUID();
        UUID planoNovoId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        PlanoAssinatura planoNovo = new PlanoAssinatura("Plano Pro", 50, 2000);
        when(planoAssinaturaService.buscarOuFalhar(planoNovoId)).thenReturn(planoNovo);
        HistoricoPlanoTenant vigenteAnterior = new HistoricoPlanoTenant(tenantId, planoAntigoId, Instant.now(), false);
        when(historicoPlanoTenantRepository.findByTenantIdAndVigenteAteIsNull(tenantId)).thenReturn(Optional.of(vigenteAnterior));
        when(usuarioRepository.countByPapelAndAtivoTrue(Papeis.MOTORISTA)).thenReturn(5L);
        when(bdoService.contarNoMesCorrente()).thenReturn(100L);

        TrocaPlanoResultado resultado = tenantPlanoService.trocarPlano(tenantId, planoNovoId);

        assertThat(vigenteAnterior.getVigenteAte()).isNotNull(); // encerrado
        assertThat(resultado.planoVigente()).isEqualTo(planoNovo);
        assertThat(resultado.usoExcedeuLimiteNaTroca()).isFalse();
        // O antigo e gravado com flush ANTES do novo (indice unico parcial de plano vigente) - ver TenantPlanoService.
        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(historicoPlanoTenantRepository);
        ordem.verify(historicoPlanoTenantRepository).saveAndFlush(vigenteAnterior);
        ordem.verify(historicoPlanoTenantRepository).save(any(HistoricoPlanoTenant.class));
    }

    @Test
    void trocarPlanoSinalizaExcedenteQuandoUsoUltrapassaNovoLimite() {
        UUID tenantId = UUID.randomUUID();
        UUID planoNovoId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        PlanoAssinatura planoRestrito = new PlanoAssinatura("Plano Basico", 2, 10);
        when(planoAssinaturaService.buscarOuFalhar(planoNovoId)).thenReturn(planoRestrito);
        when(historicoPlanoTenantRepository.findByTenantIdAndVigenteAteIsNull(tenantId)).thenReturn(Optional.empty());
        when(usuarioRepository.countByPapelAndAtivoTrue(Papeis.MOTORISTA)).thenReturn(5L); // acima do limite de 2
        when(bdoService.contarNoMesCorrente()).thenReturn(3L);

        TrocaPlanoResultado resultado = tenantPlanoService.trocarPlano(tenantId, planoNovoId);

        assertThat(resultado.usoExcedeuLimiteNaTroca()).isTrue();
    }

    @Test
    void consultarVigenteCalculaDentroDoLimiteCorretamente() {
        UUID tenantId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        PlanoAssinatura plano = new PlanoAssinatura("Plano Pro", 10, 500);
        HistoricoPlanoTenant vigente = new HistoricoPlanoTenant(tenantId, planoId, Instant.now(), false);
        when(historicoPlanoTenantRepository.findByTenantIdAndVigenteAteIsNull(tenantId)).thenReturn(Optional.of(vigente));
        when(planoAssinaturaService.buscarOuFalhar(planoId)).thenReturn(plano);
        when(usuarioRepository.countByPapelAndAtivoTrue(Papeis.MOTORISTA)).thenReturn(3L);
        when(bdoService.contarNoMesCorrente()).thenReturn(600L); // acima do limite de 500

        PlanoVigenteConsulta consulta = tenantPlanoService.consultarVigente(tenantId);

        assertThat(consulta.usoAtual().motoristasAtivos()).isEqualTo(3L);
        assertThat(consulta.usoAtual().bdosNoMesCorrente()).isEqualTo(600L);
        assertThat(consulta.dentroDoLimite()).isFalse();
    }

    @Test
    void planoIdVigenteRetornaOPlanoDoRegistroVigente() {
        UUID tenantId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        when(historicoPlanoTenantRepository.findByTenantIdAndVigenteAteIsNull(tenantId))
                .thenReturn(Optional.of(new HistoricoPlanoTenant(tenantId, planoId, Instant.now(), false)));

        assertThat(tenantPlanoService.planoIdVigente(tenantId)).isEqualTo(planoId);
    }
}
