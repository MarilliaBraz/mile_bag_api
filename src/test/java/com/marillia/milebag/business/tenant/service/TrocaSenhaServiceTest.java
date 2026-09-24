package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.model.UsuarioPlataforma;
import com.marillia.milebag.business.tenant.repository.UsuarioPlataformaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.config.security.JwtProperties;
import com.marillia.milebag.core.config.security.JwtService;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.exception.CredenciaisInvalidasException;
import com.marillia.milebag.core.exception.LoginBloqueadoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class TrocaSenhaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private UsuarioPlataformaRepository usuarioPlataformaRepository;
    @Mock
    private SessaoUsuarioService sessaoUsuarioService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(new JwtProperties("segredo-de-teste-com-mais-de-trinta-e-dois-bytes-xxxx", 60));
    private final UUID tenantId = UUID.randomUUID();
    private TrocaSenhaService service;

    @BeforeEach
    void setUp() {
        service = new TrocaSenhaService(
                usuarioRepository, usuarioPlataformaRepository, passwordEncoder, sessaoUsuarioService, jwtService, new LimitadorTentativasLogin(),
                transactionManager);
    }

    private Usuario motorista() {
        Usuario usuario = new Usuario("joao", passwordEncoder.encode("senha-antiga-1"), Papeis.MOTORISTA, "Joao");
        usuario.setId(UUID.randomUUID());
        return usuario;
    }

    @Test
    void trocaASenhaRevogaAsOutrasSessoesEDevolveTokenNovoDoMesmoUsuario() {
        Usuario usuario = motorista();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        AuthenticatedUser autenticado = new AuthenticatedUser(usuario.getId(), tenantId, Papeis.MOTORISTA);

        String token = service.trocar(autenticado, "senha-antiga-1", "senha-nova-22");

        assertThat(passwordEncoder.matches("senha-nova-22", usuario.getSenhaHash())).isTrue();
        assertThat(usuario.getTokensValidosDesde()).isNotNull();
        AuthenticatedUser novo = jwtService.validarToken(token);
        assertThat(novo.userId()).isEqualTo(usuario.getId());
        assertThat(novo.tenantId()).isEqualTo(tenantId);
        verify(sessaoUsuarioService).esquecer(usuario.getId());
    }

    @Test
    void senhaAtualErradaERecusadaENaoTrocaNada() {
        Usuario usuario = motorista();
        String hashAntes = usuario.getSenhaHash();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.trocar(new AuthenticatedUser(usuario.getId(), tenantId, Papeis.MOTORISTA), "errada", "senha-nova-22"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(usuario.getSenhaHash()).isEqualTo(hashAntes);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cincoSenhasAtuaisErradasBloqueiamATrocaMesmoComASenhaCerta() {
        Usuario usuario = motorista();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        AuthenticatedUser autenticado = new AuthenticatedUser(usuario.getId(), tenantId, Papeis.MOTORISTA);

        for (int i = 0; i < LimitadorTentativasLogin.MAXIMO_FALHAS; i++) {
            assertThatThrownBy(() -> service.trocar(autenticado, "errada", "senha-nova-22")).isInstanceOf(CredenciaisInvalidasException.class);
        }

        assertThatThrownBy(() -> service.trocar(autenticado, "senha-antiga-1", "senha-nova-22")).isInstanceOf(LoginBloqueadoException.class);
    }

    @Test
    void novaSenhaCurtaERecusadaSemConsultarNada() {
        assertThatThrownBy(() -> service.trocar(new AuthenticatedUser(UUID.randomUUID(), tenantId, Papeis.MOTORISTA), "x", "curta"))
                .isInstanceOf(RegraNegocioException.class);
        verify(usuarioRepository, never()).findById(any());
    }

    @Test
    void administradorDaPlataformaTrocaASenhaNaTabelaDele() {
        UsuarioPlataforma admin = new UsuarioPlataforma("admin", passwordEncoder.encode("12345678"));
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        when(usuarioPlataformaRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        service.trocar(new AuthenticatedUser(admin.getId(), null, Papeis.ADMINISTRADOR_PLATAFORMA), "12345678", "outra-senha-9");

        assertThat(passwordEncoder.matches("outra-senha-9", admin.getSenhaHash())).isTrue();
        verify(usuarioRepository, never()).findById(any());
    }
}
