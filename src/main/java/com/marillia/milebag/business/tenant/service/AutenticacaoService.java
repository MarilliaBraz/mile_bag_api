package com.marillia.milebag.business.tenant.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.UsuarioPlataformaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.JwtService;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.CredenciaisInvalidasException;

/**
 * Login por usuario e senha (HU22) - emite o JWT que o resto da API ja valida.
 *
 * <p>O login chega sem tenant, mas {@code usuario} tem RLS por tenant: a busca
 * tenta cada tenant conhecido (mesma estrategia de {@code TelegramWebhookController},
 * HU18) dentro de uma transacao propria, para o {@code TenantAspect} aplicar o
 * tenant. Se o mesmo login existir em dois tenants, vale o primeiro cuja senha
 * confere. O administrador da plataforma (sem tenant) e consultado primeiro.</p>
 */
@Service
public class AutenticacaoService {

    public record Sessao(String token, String papel) {
    }

    private record Credencial(UUID usuarioId, UUID tenantId, String papel) {
    }

    private final UsuarioPlataformaRepository usuarioPlataformaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LimitadorTentativasLogin limitador;
    private final TransactionTemplate transacao;
    /** Hash de uma senha qualquer, so para gastar o mesmo tempo quando o login nao existe (evita revelar logins pelo tempo de resposta). */
    private final String hashFalso;

    public AutenticacaoService(
            UsuarioPlataformaRepository usuarioPlataformaRepository, UsuarioRepository usuarioRepository, TenantService tenantService,
            PasswordEncoder passwordEncoder, JwtService jwtService, LimitadorTentativasLogin limitador,
            PlatformTransactionManager transactionManager) {
        this.usuarioPlataformaRepository = usuarioPlataformaRepository;
        this.usuarioRepository = usuarioRepository;
        this.tenantService = tenantService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.limitador = limitador;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setReadOnly(true);
        this.hashFalso = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public Sessao autenticar(String login, String senha) {
        if (login == null || login.isBlank() || senha == null || senha.isEmpty()) {
            throw new CredenciaisInvalidasException();
        }
        String loginNormalizado = login.trim();
        limitador.verificar(loginNormalizado);

        Optional<Credencial> credencial = buscarCredencial(loginNormalizado, senha);
        if (credencial.isEmpty()) {
            limitador.registrarFalha(loginNormalizado);
            throw new CredenciaisInvalidasException();
        }
        limitador.registrarSucesso(loginNormalizado);
        Credencial c = credencial.get();
        return new Sessao(jwtService.gerarToken(c.usuarioId(), c.tenantId(), c.papel()), c.papel());
    }

    /**
     * Se o login ja existe na plataforma ou em qualquer tenant (ativo ou nao). O login e unico no
     * sistema todo porque o login de {@link #autenticar} nao informa a empresa. Restaura o tenant do
     * contexto da requisicao (o de quem cadastra), que esta busca troca a cada tenant visitado.
     */
    public boolean loginJaExiste(String login) {
        String procurado = login.trim();
        if (Boolean.TRUE.equals(transacao.execute(status -> usuarioPlataformaRepository.findByLogin(procurado).isPresent()))) {
            return true;
        }
        UUID tenantDaRequisicao = TenantContext.get();
        try {
            for (UUID tenantId : tenantService.listarTodosOsIds()) {
                TenantContext.set(tenantId);
                List<Usuario> encontrados = transacao.execute(status -> usuarioRepository.findByEmailIgnoreCase(procurado));
                if (!encontrados.isEmpty()) {
                    return true;
                }
            }
            return false;
        } finally {
            TenantContext.set(tenantDaRequisicao);
        }
    }

    private Optional<Credencial> buscarCredencial(String login, String senha) {
        Optional<Credencial> plataforma = transacao.execute(status -> usuarioPlataformaRepository.findByLogin(login)
                .filter(u -> passwordEncoder.matches(senha, u.getSenhaHash()))
                .map(u -> new Credencial(u.getId(), null, Papeis.ADMINISTRADOR_PLATAFORMA)));
        if (plataforma.isPresent()) {
            return plataforma;
        }

        boolean loginEncontrado = false;
        for (UUID tenantId : tenantService.listarTodosOsIds()) {
            TenantContext.set(tenantId);
            try {
                List<Usuario> usuarios = transacao.execute(status -> usuarioRepository.findByEmailIgnoreCase(login));
                for (Usuario usuario : usuarios) {
                    loginEncontrado = true;
                    // Conta desativada (HU23) e tratada como senha errada: mesmo 401, sem revelar que o login existe.
                    if (usuario.isAtivo() && passwordEncoder.matches(senha, usuario.getSenhaHash())) {
                        return Optional.of(new Credencial(usuario.getId(), tenantId, usuario.getPapel()));
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
        if (!loginEncontrado) {
            passwordEncoder.matches(senha, hashFalso);
        }
        return Optional.empty();
    }
}
