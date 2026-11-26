package com.marillia.milebag.business.entrega.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.pagamento.service.PagamentoMotoristaService;

/** Item 4c: o motorista so acha pelo numero as entregas em que ele e o responsavel, com a data do pagamento. */
@ExtendWith(MockitoExtension.class)
class BuscaBdoMotoristaServiceTest {

    @Mock
    private BdoService bdoService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private PagamentoMotoristaService pagamentoMotoristaService;

    @Test
    void trazSoAEntregaDoMotoristaComADataDoPagamento() {
        UUID eu = UUID.randomUUID();
        UUID minhaBdo = UUID.randomUUID();
        UUID deOutroBdo = UUID.randomUUID();
        Entrega minha = entrega(minhaBdo, eu);
        Entrega deOutro = entrega(deOutroBdo, UUID.randomUUID());
        Bdo bdo = mock(Bdo.class);
        when(bdo.getNumero()).thenReturn("GYNLA25537");
        when(bdoService.buscarPorNumero("gynla 25537")).thenReturn(List.of(resposta(minhaBdo), resposta(deOutroBdo)));
        when(entregaService.buscarPorBdo(minhaBdo)).thenReturn(Optional.of(minha));
        when(entregaService.buscarPorBdo(deOutroBdo)).thenReturn(Optional.of(deOutro));
        when(bdoService.buscarPorIds(any())).thenReturn(Map.of(minhaBdo, bdo));
        LocalDate pagoEm = LocalDate.of(2026, 11, 20);
        UUID minhaId = minha.getId();
        when(pagamentoMotoristaService.pagasEntre(any())).thenReturn(Map.of(minhaId, pagoEm));

        var achadas = new BuscaBdoMotoristaService(bdoService, entregaService, pagamentoMotoristaService).buscar(eu, "gynla 25537");

        assertThat(achadas).singleElement().satisfies(achada -> {
            assertThat(achada.entregaId()).isEqualTo(minhaId);
            assertThat(achada.bdoNumero()).isEqualTo("GYNLA25537");
            assertThat(achada.pagoEm()).isEqualTo(pagoEm);
        });
    }

    private static Entrega entrega(UUID bdoId, UUID motoristaId) {
        Entrega entrega = mock(Entrega.class);
        UUID id = UUID.randomUUID();
        lenient().when(entrega.getId()).thenReturn(id);
        lenient().when(entrega.getBdoId()).thenReturn(bdoId);
        lenient().when(entrega.getMotoristaResponsavel()).thenReturn(motoristaId);
        lenient().when(entrega.getStatus()).thenReturn(EntregaStatus.ENCERRADA_COM_SUCESSO);
        lenient().when(entrega.getAtualizadoEm()).thenReturn(Instant.now());
        return entrega;
    }

    private static BdoResponse resposta(UUID id) {
        return new BdoResponse(id, null, null, null, List.of());
    }
}
