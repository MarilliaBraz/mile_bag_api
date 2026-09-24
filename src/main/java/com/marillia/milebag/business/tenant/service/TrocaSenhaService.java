package com.marillia.milebag.business.tenant.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.model.UsuarioPlataforma;
import com.marillia.milebag.business.tenant.repository.UsuarioPlataformaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.config.security.JwtService;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.exception.CredenciaisInvalidasException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * "Trocar minha senha" (HU25): o usuario logado informa a senha atual e a nova. Derruba as outras sessoes da conta
 * e devolve um token novo para quem trocou continuar logado. A senha atual errada conta no mesmo limitador de
 * tentativas do login (a sessao roubada nao serve para adivinhar a senha).
 */
@Service
public class TrocaSenhaService {

    static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final UsuarioPlataformaRepository usuarioPlataformaRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessaoUsuarioService sessaoUsuarioService;
    private final JwtService jwtService;
    private final LimitadorTentativasLogin limitador;
    private final TransactionTemplate escrita;

    public TrocaSenhaService(
            UsuarioRepository usuarioRepository, UsuarioPlataformaRepository usuarioPlataformaRepository, PasswordEncoder passwordEncoder,
            SessaoUsuarioService sessaoUsuarioService, JwtService jwtService, LimitadorTentativasLogin limitador,
            PlatformTransactionManager transactionManager) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioPlataformaRepository = usuarioPlataformaRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessaoUsuarioService = sessaoUsuarioService;
        this.jwtService = jwtService;
        this.limitador = limitador;
        this.escrita = new TransactionTemplate(transactionManager);
    }

    /** @return o token novo do proprio usuario (o antigo deixa de valer, exceto para o administrador da plataforma). */
    public String trocar(AuthenticatedUser usuario, String senhaAtual, String novaSenha) {
        if (novaSenha == null || novaSenha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("senha e obrigatoria e deve ter ao menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
        if (senhaAtual == null || senhaAtual.isEmpty()) {
            throw new CredenciaisInvalidasException();
        }
        String chaveLimite = "senha:" + usuario.userId();
        limitador.verificar(chaveLimite);

        boolean trocou = Boolean.TRUE.equals(escrita.execute(status -> Papeis.ADMINISTRADOR_PLATAFORMA.equals(usuario.papel())
                ? trocarDaPlataforma(usuario, senhaAtual, novaSenha)
                : trocarDeEmpresa(usuario, senhaAtual, novaSenha)));
        if (!trocou) {
            limitador.registrarFalha(chaveLimite);
            throw new CredenciaisInvalidasException();
        }
        limitador.registrarSucesso(chaveLimite);
        sessaoUsuarioService.esquecer(usuario.userId());
        return jwtService.gerarToken(usuario.userId(), usuario.tenantId(), usuario.papel());
    }

    private boolean trocarDeEmpresa(AuthenticatedUser autenticado, String senhaAtual, String novaSenha) {
        // O TenantContext da requisicao (definido pelo filtro JWT) faz a RLS deixar ler so a propria empresa.
        Usuario usuario = usuarioRepository.findById(autenticado.userId()).orElse(null);
        if (usuario == null || !passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            return false;
        }
        usuario.definirSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.revogarSessoesAnteriores();
        usuarioRepository.save(usuario);
        return true;
    }

    private boolean trocarDaPlataforma(AuthenticatedUser autenticado, String senhaAtual, String novaSenha) {
        UsuarioPlataforma admin = usuarioPlataformaRepository.findById(autenticado.userId()).orElse(null);
        if (admin == null || !passwordEncoder.matches(senhaAtual, admin.getSenhaHash())) {
            return false;
        }
        admin.definirSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioPlataformaRepository.save(admin);
        return true;
    }
}
