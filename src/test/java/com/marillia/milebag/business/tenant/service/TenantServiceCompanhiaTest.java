package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.model.TipoTenant;
import com.marillia.milebag.business.tenant.repository.PlanoAssinaturaRepository;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** HU33: cadastro de companhia aerea pela plataforma. */
@ExtendWith(MockitoExtension.class)
class TenantServiceCompanhiaTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private PlanoAssinaturaRepository planoAssinaturaRepository;
    @Mock
    private TenantPlanoService tenantPlanoService;
    @Mock
    private ConviteAdministradorService conviteAdministradorService;
    @Mock
    private MotivoOcorrenciaService motivoOcorrenciaService;
    @InjectMocks
    private TenantService tenantService;

    @Test
    void cadastraCompanhiaSemPlanoEConvidaOAdministrador() {
        when(tenantRepository.findByIdentificadorEmpresa("latam")).thenReturn(Optional.empty());
        when(tenantRepository.existsByCodigoIata("LA")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Tenant companhia = tenantService.cadastrarCompanhia(" latam ", "LATAM", " la ", "admin@latam.com");

        assertThat(companhia.getTipo()).isEqualTo(TipoTenant.COMPANHIA_AEREA);
        assertThat(companhia.getCodigoIata()).isEqualTo("LA");
        assertThat(companhia.getIdentificadorEmpresa()).isEqualTo("latam");
        verify(conviteAdministradorService).criarEEnviar(any(), eq("admin@latam.com"));
        // Plano e motivos de ocorrencia sao das empresas de entrega.
        verifyNoInteractions(tenantPlanoService, motivoOcorrenciaService);
    }

    @Test
    void codigoIataComLetraENumeroValeEFicaMaiusculo() {
        when(tenantRepository.findByIdentificadorEmpresa("gol")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(chamada -> chamada.getArgument(0));

        assertThat(tenantService.cadastrarCompanhia("gol", "GOL", "g3", "admin@gol.com").getCodigoIata()).isEqualTo("G3");
    }

    @Test
    void codigoIataForaDoFormatoE400() {
        for (String invalido : new String[] {"L", "LAT", "33", "L-", ""}) {
            assertThatThrownBy(() -> tenantService.cadastrarCompanhia("x", "X", invalido, "a@b.com"))
                    .as(invalido)
                    .isInstanceOf(RegraNegocioException.class);
        }
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void codigoIataRepetidoE409() {
        when(tenantRepository.findByIdentificadorEmpresa("azul")).thenReturn(Optional.empty());
        when(tenantRepository.existsByCodigoIata("AD")).thenReturn(true);

        assertThatThrownBy(() -> tenantService.cadastrarCompanhia("azul", "Azul", "AD", "a@b.com"))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Ja existe uma companhia aerea com este codigo IATA.");
    }

    @Test
    void identificadorDeEmpresaOuCompanhiaJaUsadoE409() {
        when(tenantRepository.findByIdentificadorEmpresa("dev")).thenReturn(Optional.of(new Tenant("dev", "Empresa Dev")));

        assertThatThrownBy(() -> tenantService.cadastrarCompanhia("dev", "Outra", "ZZ", "a@b.com")).isInstanceOf(ConflitoException.class);
    }
}
