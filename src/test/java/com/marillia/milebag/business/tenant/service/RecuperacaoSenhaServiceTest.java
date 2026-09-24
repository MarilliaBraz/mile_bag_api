package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.marillia.milebag.business.tenant.model.RedefinicaoSenha;
import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.RedefinicaoSenhaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.ConviteProperties;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.MessagingException;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private AutenticacaoService autenticacaoService;
    @Mock
    private RedefinicaoSenhaRepository redefinicaoSenhaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SessaoUsuarioService sessaoUsuarioService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private RecuperacaoSenhaService service;

    @BeforeEach
    void setUp() {
        service = new RecuperacaoSenhaService(
                autenticacaoService, redefinicaoSenhaRepository, usuarioRepository, passwordEncoder, sessaoUsuarioService, emailSender,
                new ConviteProperties(48, "http://app.local"), new LimitadorSolicitacoes(), transactionManager, 60);
    }

    @AfterEach
    void tenantNaoVaza() {
        assertThat(TenantContext.get()).isNull();
    }

    private String tokenDoEmail() throws MessagingException {
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(emailSender).enviar(anyString(), anyString(), corpo.capture());
        String texto = corpo.getValue();
        int inicio = texto.indexOf("/redefinir-senha/") + "/redefinir-senha/".length();
        return texto.substring(inicio, texto.indexOf('\n', inicio));
    }

    @Test
    void solicitarGravaSoOHashDoTokenEEnviaOLinkPorEmail() throws Exception {
        when(autenticacaoService.localizarContaDeTenant("ana@empresa.com"))
                .thenReturn(Optional.of(new AutenticacaoService.ContaLocalizada(usuarioId, tenantId, true)));
        when(redefinicaoSenhaRepository.findByUsuarioIdAndUsadoEmIsNull(usuarioId)).thenReturn(List.of());

        service.solicitar(" ana@empresa.com ");

        String token = tokenDoEmail();
        ArgumentCaptor<RedefinicaoSenha> salvo = ArgumentCaptor.forClass(RedefinicaoSenha.class);
        verify(redefinicaoSenhaRepository).save(salvo.capture());
        assertThat(salvo.getValue().getTokenHash()).isEqualTo(RecuperacaoSenhaService.hashDoToken(token)).isNotEqualTo(token);
        assertThat(salvo.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(salvo.getValue().getExpiraEm()).isAfter(Instant.now().plus(50, ChronoUnit.MINUTES));
        verify(emailSender).enviar(eq("ana@empresa.com"), anyString(), contains("http://app.local/redefinir-senha/" + token));
    }

    @Test
    void solicitarNaoFazNadaParaLoginSemArrobaContaInexistenteOuDesativada() throws Exception {
        service.solicitar("motorista");
        service.solicitar(null);
        when(autenticacaoService.localizarContaDeTenant("ninguem@x.com")).thenReturn(Optional.empty());
        when(autenticacaoService.localizarContaDeTenant("inativa@x.com"))
                .thenReturn(Optional.of(new AutenticacaoService.ContaLocalizada(usuarioId, tenantId, false)));

        service.solicitar("ninguem@x.com");
        service.solicitar("inativa@x.com");

        verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
        verify(redefinicaoSenhaRepository, never()).save(any());
    }

    @Test
    void novoPedidoInvalidaOsPendentesAnteriores() throws Exception {
        when(autenticacaoService.localizarContaDeTenant("ana@empresa.com"))
                .thenReturn(Optional.of(new AutenticacaoService.ContaLocalizada(usuarioId, tenantId, true)));
        RedefinicaoSenha anterior = new RedefinicaoSenha(tenantId, usuarioId, "hash-antigo", Instant.now(), Instant.now().plusSeconds(600));
        when(redefinicaoSenhaRepository.findByUsuarioIdAndUsadoEmIsNull(usuarioId)).thenReturn(List.of(anterior));

        service.solicitar("ana@empresa.com");

        assertThat(anterior.getUsadoEm()).isNotNull();
    }

    @Test
    void falhaNoEnvioDoEmailNaoQuebraNemRevelaNada() throws Exception {
        when(autenticacaoService.localizarContaDeTenant("ana@empresa.com"))
                .thenReturn(Optional.of(new AutenticacaoService.ContaLocalizada(usuarioId, tenantId, true)));
        org.mockito.Mockito.doThrow(new MessagingException("smtp fora")).when(emailSender).enviar(anyString(), anyString(), anyString());

        service.solicitar("ana@empresa.com");
    }

    @Test
    void limitaOsPedidosPorLogin() throws Exception {
        when(autenticacaoService.localizarContaDeTenant("ana@empresa.com"))
                .thenReturn(Optional.of(new AutenticacaoService.ContaLocalizada(usuarioId, tenantId, true)));

        for (int i = 0; i < LimitadorSolicitacoes.MAXIMO_POR_JANELA + 2; i++) {
            service.solicitar("ana@empresa.com");
        }

        verify(emailSender, org.mockito.Mockito.times(LimitadorSolicitacoes.MAXIMO_POR_JANELA)).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void redefinirTrocaASenhaRevogaSessoesEGastaOToken() {
        Usuario usuario = new Usuario("ana@empresa.com", passwordEncoder.encode("antiga-123"), Papeis.MOTORISTA, "Ana");
        usuario.setId(usuarioId);
        RedefinicaoSenha pedido = new RedefinicaoSenha(tenantId, usuarioId, RecuperacaoSenhaService.hashDoToken("tok-1"), Instant.now(), Instant.now().plusSeconds(600));
        when(redefinicaoSenhaRepository.findByTokenHash(RecuperacaoSenhaService.hashDoToken("tok-1"))).thenReturn(Optional.of(pedido));
        when(usuarioRepository.findById(usuarioId)).thenAnswer(chamada -> {
            assertThat(TenantContext.get()).as("a RLS precisa do tenant do pedido").isEqualTo(tenantId);
            return Optional.of(usuario);
        });

        service.redefinir("tok-1", "nova-senha-9");

        assertThat(passwordEncoder.matches("nova-senha-9", usuario.getSenhaHash())).isTrue();
        assertThat(usuario.getTokensValidosDesde()).isNotNull();
        assertThat(pedido.getUsadoEm()).isNotNull();
        verify(sessaoUsuarioService).esquecer(usuarioId);
    }

    @Test
    void redefinirRecusaTokenDesconhecidoUsadoOuExpirado() {
        when(redefinicaoSenhaRepository.findByTokenHash(RecuperacaoSenhaService.hashDoToken("usado")))
                .thenReturn(Optional.of(usadoOuExpirado(true)));
        when(redefinicaoSenhaRepository.findByTokenHash(RecuperacaoSenhaService.hashDoToken("expirado")))
                .thenReturn(Optional.of(usadoOuExpirado(false)));
        when(redefinicaoSenhaRepository.findByTokenHash(RecuperacaoSenhaService.hashDoToken("nenhum"))).thenReturn(Optional.empty());

        for (String token : List.of("usado", "expirado", "nenhum")) {
            assertThatThrownBy(() -> service.redefinir(token, "nova-senha-9")).isInstanceOf(RegraNegocioException.class);
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void redefinirExigeSenhaComOitoCaracteresAntesDeGastarOToken() {
        assertThatThrownBy(() -> service.redefinir("qualquer", "curta")).isInstanceOf(RegraNegocioException.class);
        verify(redefinicaoSenhaRepository, never()).findByTokenHash(anyString());
    }

    private RedefinicaoSenha usadoOuExpirado(boolean usado) {
        RedefinicaoSenha pedido = new RedefinicaoSenha(
                tenantId, usuarioId, "h", Instant.now().minusSeconds(7200), usado ? Instant.now().plusSeconds(600) : Instant.now().minusSeconds(60));
        if (usado) {
            pedido.marcarUsada(Instant.now());
        }
        return pedido;
    }
}
