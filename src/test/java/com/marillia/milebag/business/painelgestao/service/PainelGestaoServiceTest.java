package com.marillia.milebag.business.painelgestao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.marillia.milebag.business.painelgestao.model.ConfigPainelGestao;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoConfig;
import com.marillia.milebag.business.painelgestao.repository.PainelGestaoConfigRepository;
import com.marillia.milebag.business.painelgestao.repository.PainelGestaoRepository;
import com.marillia.milebag.business.viagem.service.AjusteViagemService;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** V63: a meta de OTD fica entre 50% e 100%; sem meta no pedido (app antigo), vale a que ja estava. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PainelGestaoServiceTest {

    @Mock
    private PainelGestaoRepository painelRepository;
    @Mock
    private PainelGestaoConfigRepository configRepository;
    @Mock
    private AjusteViagemService ajusteViagemService;
    @InjectMocks
    private PainelGestaoService servico;

    @Test
    void metaForaDaFaixaERecusadaESemMetaMantemAAtual() {
        when(configRepository.findFirstByOrderByCriadoEmAsc()).thenReturn(Optional.of(new PainelGestaoConfig(24, 4, 92)));

        assertThatThrownBy(() -> servico.salvar(new ConfigPainelGestao(24, 4, 40))).isInstanceOf(RegraNegocioException.class);
        verify(configRepository, never()).save(any());

        assertThat(servico.salvar(new ConfigPainelGestao(48, 6, null))).isEqualTo(new ConfigPainelGestao(48, 6, 92));
    }
}
