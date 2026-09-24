package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.config.security.Papeis;

@ExtendWith(MockitoExtension.class)
class SessaoUsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SessaoUsuarioService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SessaoUsuarioService(usuarioRepository, transactionManager);
    }

    private Usuario conta() {
        Usuario conta = new Usuario("joao", "hash", Papeis.MOTORISTA, "Joao");
        conta.setId(UUID.randomUUID());
        return conta;
    }

    private AuthenticatedUser token(Usuario conta, Instant emitidoEm) {
        return new AuthenticatedUser(conta.getId(), tenantId, Papeis.MOTORISTA, emitidoEm);
    }

    @Test
    void contaAtivaSemRevogacaoTemSessaoValida() {
        Usuario conta = conta();
        when(usuarioRepository.findById(conta.getId())).thenReturn(Optional.of(conta));

        assertThat(service.sessaoValida(token(conta, Instant.now()))).isTrue();
    }

    @Test
    void contaDesativadaNaoTemSessaoValida() {
        Usuario conta = conta();
        conta.definirAtivo(false);
        when(usuarioRepository.findById(conta.getId())).thenReturn(Optional.of(conta));

        assertThat(service.sessaoValida(token(conta, Instant.now()))).isFalse();
    }

    @Test
    void tokenEmitidoAntesDaRevogacaoEInvalidoEODepoisEValido() {
        Usuario conta = conta();
        conta.revogarSessoesAnteriores();
        when(usuarioRepository.findById(conta.getId())).thenReturn(Optional.of(conta));

        assertThat(service.sessaoValida(token(conta, Instant.now().minus(10, ChronoUnit.MINUTES)))).isFalse();
        assertThat(service.sessaoValida(token(conta, Instant.now().plus(5, ChronoUnit.SECONDS)))).isTrue();
    }

    @Test
    void tokenEmitidoNoMesmoSegundoDaRevogacaoContinuaValido() {
        Usuario conta = conta();
        conta.revogarSessoesAnteriores();
        when(usuarioRepository.findById(conta.getId())).thenReturn(Optional.of(conta));

        // O iat do JWT so tem precisao de segundos: um login logo apos trocar a senha nao pode ser recusado.
        assertThat(service.sessaoValida(token(conta, conta.getTokensValidosDesde().truncatedTo(ChronoUnit.SECONDS)))).isTrue();
    }

    @Test
    void contaInexistenteNaoTemSessaoValida() {
        Usuario conta = conta();
        when(usuarioRepository.findById(conta.getId())).thenReturn(Optional.empty());

        assertThat(service.sessaoValida(token(conta, Instant.now()))).isFalse();
    }

    @Test
    void administradorDaPlataformaNaoPassaPelaChecagem() {
        assertThat(service.sessaoValida(new AuthenticatedUser(UUID.randomUUID(), null, Papeis.ADMINISTRADOR_PLATAFORMA, Instant.now()))).isTrue();
    }

    @Test
    void resultadoFicaEmCacheEEsquecerForcaNovaLeitura() {
        Usuario conta = conta();
        when(usuarioRepository.findById(conta.getId())).thenReturn(Optional.of(conta));

        service.sessaoValida(token(conta, Instant.now()));
        service.sessaoValida(token(conta, Instant.now()));
        verify(usuarioRepository, times(1)).findById(conta.getId());

        conta.definirAtivo(false);
        assertThat(service.sessaoValida(token(conta, Instant.now()))).as("ainda em cache").isTrue();

        service.esquecer(conta.getId());
        assertThat(service.sessaoValida(token(conta, Instant.now()))).isFalse();
    }
}
