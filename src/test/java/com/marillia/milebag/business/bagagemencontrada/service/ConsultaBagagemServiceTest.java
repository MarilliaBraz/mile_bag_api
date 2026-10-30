package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLocalizacao;
import com.marillia.milebag.business.bagagemencontrada.model.PaginaBagagens;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.geo.MapaEstatico;

@ExtendWith(MockitoExtension.class)
class ConsultaBagagemServiceTest {

    @Mock
    private BagagemEncontradaRepository repository;
    @Mock
    private BagagemEncontradaService bagagemService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private MapaEstatico mapaEstatico;

    private ConsultaBagagemService service;
    private final ConsultaBagagens semFiltros = ConsultaBagagens.de(null, null, null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        service = new ConsultaBagagemService(repository, bagagemService, tenantRepository, mapaEstatico);
        TenantContext.set(UUID.randomUUID());
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    void paginaDe50PorPadrao() {
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<BagagemEncontrada>>any(), any(Pageable.class))).thenAnswer(chamada -> {
            Pageable pedido = chamada.getArgument(1);
            assertThat(pedido.getPageSize()).isEqualTo(50);
            assertThat(pedido.getSort().getOrderFor("registradaEm")).isNotNull();
            return new PageImpl<BagagemEncontrada>(java.util.List.of(), pedido, 132);
        });

        PaginaBagagens pagina = service.consultar(semFiltros, null, null);

        assertThat(pagina.total()).isEqualTo(132);
        assertThat(pagina.tamanho()).isEqualTo(50);
    }

    @Test
    void recusaPaginacaoForaDosLimites() {
        assertThatThrownBy(() -> service.consultar(semFiltros, -1, 50)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.consultar(semFiltros, 0, 101)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.consultar(semFiltros, 0, 0)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void rascunhoNaoTemFicha() {
        BagagemEncontrada rascunho = BagagemEncontrada.rascunhoSemEtiqueta(
                UUID.randomUUID(), "foto.jpg", CorBagagem.AZUL, -23.43, -46.47, OrigemLocalizacao.FOTO, "GRU", 100);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(rascunho));

        assertThatThrownBy(() -> service.ficha(id)).isInstanceOf(ConflitoException.class);
        verify(mapaEstatico, never()).desenhar(anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void fichaDeOutraCompanhiaNaoExiste() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ficha(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
