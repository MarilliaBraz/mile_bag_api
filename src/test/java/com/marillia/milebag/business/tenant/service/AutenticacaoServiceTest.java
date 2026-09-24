package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.model.UsuarioLogin;
import com.marillia.milebag.business.tenant.model.UsuarioPlataforma;
import com.marillia.milebag.business.tenant.repository.UsuarioLoginRepository;
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
    private UsuarioLoginRepository usuarioLoginRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(new JwtProperties("segredo-de-teste-com-mais-de-trinta-e-dois-bytes-xxxx", 60));
    private final UUID tenantB = UUID.randomUUID();

    private AutenticacaoService service;

    @BeforeEach
    void setUp() {
        service = new AutenticacaoService(
                usuarioPlataformaRepository, usuarioLoginRepository, usuarioRepository, passwordEncoder, jwtService, new LimitadorTentativasLogin(),
                transactionManager);
    }

    @AfterEach
    void limparTenant() {
        assertThat(TenantContext.get()).as("o tenant nao pode vazar da busca de login").isNull();
    }

    private UsuarioPlataforma admin() {
        UsuarioPlataforma admin = new UsuarioPlataforma("admin", passwordEncoder.encode("12345678"));
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        return admin;
    }

    private Usuario motorista(String login, String senha) {
        Usuario usuario = new Usuario(login, passwordEncoder.encode(senha), Papeis.MOTORISTA, "Joao");
        usuario.setId(UUID.randomUUID());
        return usuario;
    }

    /** O mapa e so leitura (o trigger o escreve): no teste ele nasce por reflexao. */
    private UsuarioLogin mapaDe(String login, UUID tenantId, UUID usuarioId) {
        try {
            var construtor = UsuarioLogin.class.getDeclaredConstructor();
            construtor.setAccessible(true);
            UsuarioLogin mapa = construtor.newInstance();
            ReflectionTestUtils.setField(mapa, "login", login);
            ReflectionTestUtils.setField(mapa, "tenantId", tenantId);
            ReflectionTestUtils.setField(mapa, "usuarioId", usuarioId);
            return mapa;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void administradorDaPlataformaEntraSemTenantNoToken() {
        when(usuarioPlataformaRepository.findByLogin("admin")).thenReturn(Optional.of(admin()));

        var sessao = service.autenticar("admin", "12345678");

        AuthenticatedUser usuario = jwtService.validarToken(sessao.token());
        assertThat(sessao.papel()).isEqualTo(Papeis.ADMINISTRADOR_PLATAFORMA);
        assertThat(usuario.tenantId()).isNull();
        verify(usuarioLoginRepository, never()).findById(any());
    }

    @Test
    void usuarioDeEmpresaVaiDiretoAoTenantIndicadoPeloMapa() {
        Usuario motorista = motorista("joao", "12345678");
        when(usuarioPlataformaRepository.findByLogin("joao")).thenReturn(Optional.empty());
        when(usuarioLoginRepository.findById("joao")).thenReturn(Optional.of(mapaDe("joao", tenantB, motorista.getId())));
        // Simula a RLS: so devolve o usuario quando o tenant do contexto e o do mapa.
        when(usuarioRepository.findById(motorista.getId()))
                .thenAnswer(chamada -> tenantB.equals(TenantContext.get()) ? Optional.of(motorista) : Optional.empty());

        var sessao = service.autenticar("joao", "12345678");

        AuthenticatedUser usuario = jwtService.validarToken(sessao.token());
        assertThat(usuario.papel()).isEqualTo(Papeis.MOTORISTA);
        assertThat(usuario.tenantId()).isEqualTo(tenantB);
    }

    @Test
    void loginEBuscadoEmMinusculasNoMapa() {
        Usuario motorista = motorista("Joao@Empresa.com", "12345678");
        when(usuarioPlataformaRepository.findByLogin("JOAO@empresa.com")).thenReturn(Optional.empty());
        when(usuarioLoginRepository.findById("joao@empresa.com")).thenReturn(Optional.of(mapaDe("joao@empresa.com", tenantB, motorista.getId())));
        when(usuarioRepository.findById(motorista.getId())).thenReturn(Optional.of(motorista));

        assertThat(service.autenticar(" JOAO@empresa.com ", "12345678").papel()).isEqualTo(Papeis.MOTORISTA);
    }

    @Test
    void senhaErradaELoginInexistenteDaoOMesmoErro() {
        when(usuarioPlataformaRepository.findByLogin("admin")).thenReturn(Optional.of(admin()));
        when(usuarioPlataformaRepository.findByLogin("ninguem")).thenReturn(Optional.empty());
        when(usuarioLoginRepository.findById("admin")).thenReturn(Optional.empty());
        when(usuarioLoginRepository.findById("ninguem")).thenReturn(Optional.empty());

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
        when(usuarioPlataformaRepository.findByLogin("admin")).thenReturn(Optional.of(admin()));
        when(usuarioLoginRepository.findById("admin")).thenReturn(Optional.empty());

        for (int i = 0; i < LimitadorTentativasLogin.MAXIMO_FALHAS; i++) {
            assertThatThrownBy(() -> service.autenticar("admin", "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        }

        assertThatThrownBy(() -> service.autenticar("admin", "12345678")).isInstanceOf(LoginBloqueadoException.class);
    }

    @Test
    void loginCertoZeraOContadorDeFalhas() {
        when(usuarioPlataformaRepository.findByLogin("admin")).thenReturn(Optional.of(admin()));
        when(usuarioLoginRepository.findById("admin")).thenReturn(Optional.empty());

        for (int i = 0; i < LimitadorTentativasLogin.MAXIMO_FALHAS - 1; i++) {
            assertThatThrownBy(() -> service.autenticar("admin", "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        }
        service.autenticar("admin", "12345678");
        assertThatThrownBy(() -> service.autenticar("admin", "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        service.autenticar("admin", "12345678");
    }

    @Test
    void motoristaDesativadoNaoEntraEDaOMesmoErroDeSenhaErrada() {
        Usuario motorista = motorista("joao", "12345678");
        motorista.definirAtivo(false);
        when(usuarioPlataformaRepository.findByLogin("joao")).thenReturn(Optional.empty());
        when(usuarioLoginRepository.findById("joao")).thenReturn(Optional.of(mapaDe("joao", tenantB, motorista.getId())));
        when(usuarioRepository.findById(motorista.getId())).thenReturn(Optional.of(motorista));

        assertThatThrownBy(() -> service.autenticar("joao", "12345678")).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void loginJaExisteConsultaAPlataformaEOMapaSemMexerNoTenantDaRequisicao() {
        UUID tenantDoAdmin = UUID.randomUUID();
        TenantContext.set(tenantDoAdmin);
        try {
            when(usuarioPlataformaRepository.findByLogin("joao")).thenReturn(Optional.empty());
            when(usuarioLoginRepository.existsById("joao")).thenReturn(true);

            assertThat(service.loginJaExiste(" joao ")).isTrue();
            assertThat(TenantContext.get()).isEqualTo(tenantDoAdmin);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void localizarContaLeNoTenantDoMapaERestauraOContextoDaRequisicao() {
        UUID tenantDaRequisicao = UUID.randomUUID();
        Usuario ana = motorista("ana@empresa.com", "12345678");
        TenantContext.set(tenantDaRequisicao);
        try {
            when(usuarioLoginRepository.findById("ana@empresa.com")).thenReturn(Optional.of(mapaDe("ana@empresa.com", tenantB, ana.getId())));
            when(usuarioRepository.findById(ana.getId()))
                    .thenAnswer(chamada -> tenantB.equals(TenantContext.get()) ? Optional.of(ana) : Optional.empty());

            var conta = service.localizarContaDeTenant("Ana@Empresa.com");

            assertThat(conta).isPresent();
            assertThat(conta.get().tenantId()).isEqualTo(tenantB);
            assertThat(conta.get().ativo()).isTrue();
            assertThat(TenantContext.get()).isEqualTo(tenantDaRequisicao);
        } finally {
            TenantContext.clear();
        }
    }
}
