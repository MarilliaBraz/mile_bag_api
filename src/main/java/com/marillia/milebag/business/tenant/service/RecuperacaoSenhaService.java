package com.marillia.milebag.business.tenant.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.tenant.model.RedefinicaoSenha;
import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.RedefinicaoSenhaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.ConviteProperties;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.MessagingException;

/**
 * "Esqueci minha senha" (HU25): o usuario informa o login, recebe por e-mail um link de uso unico e define a senha
 * nova. So funciona para conta cujo login e um e-mail (o link vai para ele); para os demais o motorista pede ao
 * administrador (HU23).
 *
 * <p>{@link #solicitar} nunca revela se o login existe: responde igual para conta inexistente, desativada, sem
 * e-mail ou limitada, e um erro de envio so vai para o log. O token vai so no e-mail; no banco fica o hash.</p>
 */
@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    static final int TAMANHO_MINIMO_SENHA = 8;

    private final AutenticacaoService autenticacaoService;
    private final RedefinicaoSenhaRepository redefinicaoSenhaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessaoUsuarioService sessaoUsuarioService;
    private final EmailSender emailSender;
    private final ConviteProperties conviteProperties;
    private final LimitadorSolicitacoes limitador;
    private final Duration validade;
    private final TransactionTemplate escrita;

    public RecuperacaoSenhaService(
            AutenticacaoService autenticacaoService, RedefinicaoSenhaRepository redefinicaoSenhaRepository, UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder, SessaoUsuarioService sessaoUsuarioService, EmailSender emailSender, ConviteProperties conviteProperties,
            LimitadorSolicitacoes limitador, PlatformTransactionManager transactionManager,
            @Value("${milebag.recuperacao-senha.validade-minutos:60}") long validadeMinutos) {
        this.autenticacaoService = autenticacaoService;
        this.redefinicaoSenhaRepository = redefinicaoSenhaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessaoUsuarioService = sessaoUsuarioService;
        this.emailSender = emailSender;
        this.conviteProperties = conviteProperties;
        this.limitador = limitador;
        this.validade = Duration.ofMinutes(validadeMinutos);
        this.escrita = new TransactionTemplate(transactionManager);
    }

    public void solicitar(String login) {
        if (login == null || login.isBlank() || !login.contains("@")) {
            return;
        }
        String loginLimpo = login.trim();
        if (!limitador.permitir(loginLimpo)) {
            log.warn("Pedido de redefinicao de senha acima do limite para um login - ignorado.");
            return;
        }
        Optional<AutenticacaoService.ContaLocalizada> conta = autenticacaoService.localizarContaDeTenant(loginLimpo);
        if (conta.isEmpty() || !conta.get().ativo()) {
            return;
        }

        String token = gerarToken();
        Instant agora = Instant.now();
        escrita.executeWithoutResult(status -> {
            // Um pedido novo invalida os anteriores ainda pendentes do mesmo usuario.
            redefinicaoSenhaRepository.findByUsuarioIdAndUsadoEmIsNull(conta.get().usuarioId()).forEach(anterior -> anterior.marcarUsada(agora));
            redefinicaoSenhaRepository.save(new RedefinicaoSenha(conta.get().tenantId(), conta.get().usuarioId(), hash(token), agora, agora.plus(validade)));
        });

        String link = conviteProperties.urlBase() + "/redefinir-senha/" + token;
        String corpo = "Recebemos um pedido para redefinir a senha da sua conta no Milebag.\n\n"
                + "Para escolher uma senha nova, acesse: " + link + "\n\n"
                + "O link vale por " + validade.toMinutes() + " minutos e so pode ser usado uma vez. "
                + "Se voce nao pediu isso, ignore este e-mail: sua senha continua a mesma.";
        try {
            emailSender.enviar(loginLimpo, "Milebag - redefinicao de senha", corpo);
        } catch (MessagingException | RuntimeException e) {
            log.error("Falha ao enviar o e-mail de redefinicao de senha - o usuario pode pedir de novo.", e);
        }
    }

    public void redefinir(String token, String novaSenha) {
        if (novaSenha == null || novaSenha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("senha e obrigatoria e deve ter ao menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
        RedefinicaoSenha pedido = token == null ? null : redefinicaoSenhaRepository.findByTokenHash(hash(token.trim())).orElse(null);
        Instant agora = Instant.now();
        if (pedido == null || !pedido.estaValida(agora)) {
            throw new RegraNegocioException("Link de redefinicao invalido ou expirado. Peca um novo.");
        }

        // O usuario esta deslogado: o tenant vem do pedido, para a RLS de "usuario" deixar ler e gravar a conta dele.
        TenantContext.set(pedido.getTenantId());
        try {
            escrita.executeWithoutResult(status -> {
                Usuario usuario = usuarioRepository.findById(pedido.getUsuarioId())
                        .orElseThrow(() -> new RegraNegocioException("Link de redefinicao invalido ou expirado. Peca um novo."));
                usuario.definirSenhaHash(passwordEncoder.encode(novaSenha));
                usuario.revogarSessoesAnteriores();
                usuarioRepository.save(usuario);
                pedido.marcarUsada(agora);
                redefinicaoSenhaRepository.save(pedido);
            });
        } finally {
            TenantContext.clear();
        }
        sessaoUsuarioService.esquecer(pedido.getUsuarioId());
    }

    private static String gerarToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel.", e);
        }
    }

    /** Somente para os testes reconstruirem o hash de um token conhecido. */
    static String hashDoToken(String token) {
        return hash(token);
    }

}
