package com.marillia.milebag.business.tenant.service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.model.UsuarioLogin;
import com.marillia.milebag.business.tenant.repository.UsuarioLoginRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioPlataformaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.JwtService;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.CredenciaisInvalidasException;

/**
 * Login por usuario e senha (HU22) - emite o JWT que o resto da API ja valida.
 *
 * <p>O login chega sem tenant, mas {@code usuario} tem RLS por tenant. A tabela {@code usuario_login} (V32,
 * mantida por trigger, sem RLS) diz de qual empresa e cada login, entao a busca vai direto ao tenant certo, em
 * vez de tentar todos (HU26). O administrador da plataforma (sem tenant) e consultado primeiro.</p>
 */
@Service
public class AutenticacaoService {

    public record Sessao(String token, String papel) {
    }

    private record Credencial(UUID usuarioId, UUID tenantId, String papel) {
    }

    /** Uma conta de empresa encontrada pelo login, sem a senha (HU25: recuperacao de senha). */
    public record ContaLocalizada(UUID usuarioId, UUID tenantId, boolean ativo) {
    }

    private final UsuarioPlataformaRepository usuarioPlataformaRepository;
    private final UsuarioLoginRepository usuarioLoginRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LimitadorTentativasLogin limitador;
    private final TransactionTemplate transacao;
    /** Hash de uma senha qualquer, so para gastar o mesmo tempo quando o login nao existe (evita revelar logins pelo tempo de resposta). */
    private final String hashFalso;

    public AutenticacaoService(
            UsuarioPlataformaRepository usuarioPlataformaRepository, UsuarioLoginRepository usuarioLoginRepository, UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder, JwtService jwtService, LimitadorTentativasLogin limitador, PlatformTransactionManager transactionManager) {
        this.usuarioPlataformaRepository = usuarioPlataformaRepository;
        this.usuarioLoginRepository = usuarioLoginRepository;
        this.usuarioRepository = usuarioRepository;
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

    /** Procura a conta de EMPRESA com este login (ignora maiusculas); o administrador da plataforma nao entra aqui. */
    public Optional<ContaLocalizada> localizarContaDeTenant(String login) {
        return usuarioLoginRepository.findById(chave(login))
                .flatMap(mapa -> lerConta(mapa).map(conta -> new ContaLocalizada(conta.getId(), mapa.getTenantId(), conta.isAtivo())));
    }

    /**
     * Se o login ja existe na plataforma ou em qualquer tenant (ativo ou nao). O login e unico no
     * sistema todo porque o login de {@link #autenticar} nao informa a empresa.
     */
    public boolean loginJaExiste(String login) {
        String procurado = login.trim();
        if (Boolean.TRUE.equals(transacao.execute(status -> usuarioPlataformaRepository.findByLogin(procurado).isPresent()))) {
            return true;
        }
        return usuarioLoginRepository.existsById(chave(login));
    }

    private Optional<Credencial> buscarCredencial(String login, String senha) {
        Optional<Credencial> plataforma = transacao.execute(status -> usuarioPlataformaRepository.findByLogin(login)
                .filter(u -> passwordEncoder.matches(senha, u.getSenhaHash()))
                .map(u -> new Credencial(u.getId(), null, Papeis.ADMINISTRADOR_PLATAFORMA)));
        if (plataforma.isPresent()) {
            return plataforma;
        }

        Optional<UsuarioLogin> mapa = usuarioLoginRepository.findById(chave(login));
        if (mapa.isEmpty()) {
            passwordEncoder.matches(senha, hashFalso);
            return Optional.empty();
        }
        // Conta desativada (HU23) e tratada como senha errada: mesmo 401, sem revelar que o login existe.
        return lerConta(mapa.get())
                .filter(usuario -> usuario.isAtivo() && passwordEncoder.matches(senha, usuario.getSenhaHash()))
                .map(usuario -> new Credencial(usuario.getId(), mapa.get().getTenantId(), usuario.getPapel()));
    }

    /** Le a conta no tenant dela (a RLS de {@code usuario} exige o tenant no contexto) e restaura o contexto que havia. */
    private Optional<Usuario> lerConta(UsuarioLogin mapa) {
        UUID tenantAnterior = TenantContext.get();
        TenantContext.set(mapa.getTenantId());
        try {
            return transacao.execute(status -> usuarioRepository.findById(mapa.getUsuarioId()));
        } finally {
            TenantContext.set(tenantAnterior);
        }
    }

    private static String chave(String login) {
        return login.trim().toLowerCase(Locale.ROOT);
    }
}
