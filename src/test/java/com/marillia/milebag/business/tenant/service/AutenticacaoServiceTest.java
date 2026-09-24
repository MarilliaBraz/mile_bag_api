package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.CredenciaisInvalidasException;
import com.marillia.milebag.core.exception.LoginBloqueadoException;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {

    @Mock
    private UsuarioPlataformaRepository usuarioPlataformaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TenantService tenantService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(new JwtProperties("segredo-de-teste-com-mais-de-trinta-e-dois-bytes-xxxx", 60));
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();

    private AutenticacaoService service;

    @BeforeEach
    void setUp() {
        service = new AutenticacaoService(
                usuarioPlataformaRepository, usuarioRepository, tenantService, passwordEncoder, jwtService, new LimitadorTentativasLogin(),
                transactionManager);
    }

    private UsuarioPlataforma admin() {
        UsuarioPlataforma admin = new UsuarioPlataforma("admin", passwordEncoder.encode("12345678"));
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        return admin;
    }

    @AfterEach
    void limparTenant() {
        assertThat(TenantContext.get()).as("o tenant nao pode vazar da busca de login").isNull();
    }

    @Test
    void administradorDaPlataformaEntraSemTenantNoToken() {
        when(usuarioPlataformaRepository.findByLogin("admin")).thenReturn(Optional.of(admin()));

        var sessao = service.autenticar("admin", "12345678");

        AuthenticatedUser usuario = jwtService.validarToken(sessao.token());
        assertThat(sessao.papel()).isEqualTo(Papeis.ADMINISTRADOR_PLATAFORMA);
        assertThat(usuario.papel()).isEqualTo(Papeis.ADMINISTRADOR_PLATAFORMA);
        assertThat(usuario.tenantId()).isNull();
    }

    @Test
    void usuarioDeTenantEEncontradoNoTenantCerto() {
        when(usuarioPlataformaRepository.findByLogin("motorista")).thenReturn(Optional.empty());
        when(tenantService.listarTodosOsIds()).thenReturn(List.of(tenantA, tenantB));
        Usuario motorista = new Usuario("motorista", passwordEncoder.encode("12345678"), Papeis.MOTORISTA);
        motorista.setId(UUID.randomUUID());
        // Simula a RLS: so devolve o usuario quando o tenant do contexto e o dele.
        when(usuarioRepository.findByEmailIgnoreCase("motorista"))
                .thenAnswer(chamada -> tenantB.equals(TenantContext.get()) ? List.of(motorista) : List.of());

        var sessao = service.autenticar("motorista", "12345678");

        AuthenticatedUser usuario = jwtService.validarToken(sessao.token());
        assertThat(usuario.papel()).isEqualTo(Papeis.MOTORISTA);
        assertThat(usuario.tenantId()).isEqualTo(tenantB);
    }

    @Test
    void senhaErradaELoginInexistenteDaoOMesmoErro() {
        when(usuarioPlataformaRepository.findByLogin("admin"))
                .thenReturn(Optional.of(admin()));
        when(usuarioPlataformaRepository.findByLogin("ninguem")).thenReturn(Optional.empty());
        when(tenantService.listarTodosOsIds()).thenReturn(List.of(tenantA));
        when(usuarioRepository.findByEmailIgnoreCase("admin")).thenReturn(List.of());
        when(usuarioRepository.findByEmailIgnoreCase("ninguem")).thenReturn(List.of());

        assertThatThrownBy(() -> service.autenticar("admin", "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> service.autenticar("ninguem", "qualquer")).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void camposVaziosSaoRecusadosSemConsultarOBanco() {
        assertThatThrownBy(() -> service.autenticar("", "x")).isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> service.autenticar("admin", "")).isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> service.autenticar(null, null)).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void depoisDeCincoSenhasErradasOLoginFicaBloqueadoMesmoComASenhaCerta() {
        when(usuarioPlataformaRepository.findByLogin("admin"))
                .thenReturn(Optional.of(admin()));
        when(tenantService.listarTodosOsIds()).thenReturn(List.of());

        for (int i = 0; i < LimitadorTentativasLogin.MAXIMO_FALHAS; i++) {
            assertThatThrownBy(() -> service.autenticar("admin", "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        }

        assertThatThrownBy(() -> service.autenticar("admin", "12345678")).isInstanceOf(LoginBloqueadoException.class);
    }

    @Test
    void loginCertoZeraOContadorDeFalhas() {
        when(usuarioPlataformaRepository.findByLogin("admin"))
                .thenReturn(Optional.of(admin()));
        when(tenantService.listarTodosOsIds()).thenReturn(List.of());

        for (int i = 0; i < LimitadorTentativasLogin.MAXIMO_FALHAS - 1; i++) {
            assertThatThrownBy(() -> service.autenticar("admin", "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        }
        service.autenticar("admin", "12345678");
        assertThatThrownBy(() -> service.autenticar("admin", "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        service.autenticar("admin", "12345678");
    }

    @Test
    void motoristaDesativadoNaoEntraEDaOMesmoErroDeSenhaErrada() {
        when(usuarioPlataformaRepository.findByLogin("joao")).thenReturn(Optional.empty());
        when(tenantService.listarTodosOsIds()).thenReturn(List.of(tenantA));
        Usuario motorista = new Usuario("joao", passwordEncoder.encode("12345678"), Papeis.MOTORISTA, "Joao");
        motorista.setId(UUID.randomUUID());
        motorista.definirAtivo(false);
        when(usuarioRepository.findByEmailIgnoreCase("joao")).thenReturn(List.of(motorista));

        assertThatThrownBy(() -> service.autenticar("joao", "12345678")).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void loginJaExisteProcuraEmTodosOsTenantsEDevolveOTenantDaRequisicao() {
        UUID tenantDoAdmin = UUID.randomUUID();
        TenantContext.set(tenantDoAdmin);
        try {
            when(usuarioPlataformaRepository.findByLogin("joao")).thenReturn(Optional.empty());
            when(tenantService.listarTodosOsIds()).thenReturn(List.of(tenantA, tenantB));
            Usuario existente = new Usuario("joao", "hash", Papeis.MOTORISTA);
            when(usuarioRepository.findByEmailIgnoreCase("joao"))
                    .thenAnswer(chamada -> tenantB.equals(TenantContext.get()) ? List.of(existente) : List.of());

            assertThat(service.loginJaExiste(" joao ")).isTrue();
            assertThat(TenantContext.get()).as("o tenant de quem cadastra tem que ser restaurado").isEqualTo(tenantDoAdmin);
        } finally {
            TenantContext.clear();
        }
    }
}
