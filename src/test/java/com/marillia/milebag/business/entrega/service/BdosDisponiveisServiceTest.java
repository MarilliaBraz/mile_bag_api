package com.marillia.milebag.business.entrega.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoOrigem;
import com.marillia.milebag.business.bdo.model.PendenciaContatoBdo;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.business.entrega.mapper.PainelEntregasMapper;
import com.marillia.milebag.business.entrega.model.BdosDisponiveisResponse;

/** Cobre HU19 (FR-007): lista de BDOs confirmados sem motorista responsavel. */
@ExtendWith(MockitoExtension.class)
class BdosDisponiveisServiceTest {

    @Mock
    private BdoRepository bdoRepository;

    private BdosDisponiveisService bdosDisponiveisService;

    @BeforeEach
    void setUp() {
        bdosDisponiveisService = new BdosDisponiveisService(bdoRepository, new PainelEntregasMapper());
    }

    @Test
    void consultarDisponiveisMapeiaOrigemEDadosResumidos() {
        UUID bdoId = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setId(bdoId);
        bdo.setOrigem(BdoOrigem.EMAIL_AUTOMATICO);
        bdo.setNumero("BDO-999");
        bdo.setPassageiroNome("Ciclana");
        bdo.setEnderecoEntrega("Av. Teste, 456");
        bdo.setDataCaptura(Instant.now());
        when(bdoRepository.findConfirmadosSemEntrega()).thenReturn(List.of(bdo));

        BdosDisponiveisResponse resposta = bdosDisponiveisService.consultarDisponiveis();

        assertThat(resposta.bdos()).hasSize(1);
        var item = resposta.bdos().get(0);
        assertThat(item.bdoId()).isEqualTo(bdoId);
        assertThat(item.origem()).isEqualTo(BdoOrigem.EMAIL_AUTOMATICO);
        assertThat(item.bdoNumero()).isEqualTo("BDO-999");
    }

    @Test
    void consultarDisponiveisSinalizaContatoIncompletoSemTirarDaLista() {
        Bdo bdo = new Bdo();
        bdo.setId(UUID.randomUUID());
        bdo.setOrigem(BdoOrigem.EMAIL_AUTOMATICO);
        bdo.setEnderecoEntrega("Rua Sem Numero, Centro");
        bdo.setDataCaptura(Instant.now());
        when(bdoRepository.findConfirmadosSemEntrega()).thenReturn(List.of(bdo));

        var item = bdosDisponiveisService.consultarDisponiveis().bdos().get(0);

        assertThat(item.pendenciasContato())
                .containsExactly(PendenciaContatoBdo.ENDERECO_SEM_NUMERO, PendenciaContatoBdo.TELEFONE_AUSENTE);
    }

    @Test
    void consultarDisponiveisVazioNaoQuebra() {
        when(bdoRepository.findConfirmadosSemEntrega()).thenReturn(List.of());

        assertThat(bdosDisponiveisService.consultarDisponiveis().bdos()).isEmpty();
    }
}
