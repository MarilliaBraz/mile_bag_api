package com.marillia.milebag.business.tenant.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Cadastro de motoristas pelo administrador do tenant (HU23). Um motorista e um
 * {@link Usuario} com papel {@code MOTORISTA}: o id dele e o {@code sub} do JWT,
 * o mesmo id que as entregas, ajustes e fechamentos ja guardam.
 *
 * <p>Sem {@code @Transactional} de proposito: a checagem de login unico percorre
 * os tenants e nao pode rodar dentro da transacao da escrita (ver
 * {@link AutenticacaoService#loginJaExiste}); cada escrita abre a sua.</p>
 */
@Service
public class MotoristaService {

    static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final AutenticacaoService autenticacaoService;
    private final PasswordEncoder passwordEncoder;
    private final SessaoUsuarioService sessaoUsuarioService;
    private final TransactionTemplate escrita;
    private final TransactionTemplate leitura;

    public MotoristaService(
            UsuarioRepository usuarioRepository, AutenticacaoService autenticacaoService, PasswordEncoder passwordEncoder,
            SessaoUsuarioService sessaoUsuarioService, PlatformTransactionManager transactionManager) {
        this.sessaoUsuarioService = sessaoUsuarioService;
        this.usuarioRepository = usuarioRepository;
        this.autenticacaoService = autenticacaoService;
        this.passwordEncoder = passwordEncoder;
        this.escrita = new TransactionTemplate(transactionManager);
        this.leitura = new TransactionTemplate(transactionManager);
        this.leitura.setReadOnly(true);
    }

    public List<Usuario> listar() {
        return leitura.execute(status -> usuarioRepository.findByPapelOrderByNomeAscEmailAsc(Papeis.MOTORISTA));
    }

    public Usuario criar(String nome, String login, String senha) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("nome e obrigatorio.");
        }
        if (login == null || login.isBlank()) {
            throw new RegraNegocioException("login e obrigatorio.");
        }
        validarSenha(senha);
        String loginLimpo = login.trim();
        if (autenticacaoService.loginJaExiste(loginLimpo)) {
            throw new ConflitoException("Ja existe uma conta com este login.");
        }
        try {
            return escrita.execute(status -> usuarioRepository.save(new Usuario(loginLimpo, passwordEncoder.encode(senha), Papeis.MOTORISTA, nome.trim())));
        } catch (DataIntegrityViolationException e) {
            // Corrida entre dois cadastros com o mesmo login: quem perde e barrado pelo indice unico (V29).
            throw new ConflitoException("Ja existe uma conta com este login.");
        }
    }

    /** PATCH parcial: aplica o nome e/ou o estado informados (ao menos um). */
    public Usuario atualizar(UUID id, String nome, Boolean ativo) {
        if (nome == null && ativo == null) {
            throw new RegraNegocioException("informe nome e/ou ativo.");
        }
        Usuario atual = null;
        if (nome != null) {
            atual = renomear(id, nome);
        }
        if (ativo != null) {
            atual = definirAtivo(id, ativo);
        }
        return atual;
    }

    public Usuario definirAtivo(UUID id, Boolean ativo) {
        if (ativo == null) {
            throw new RegraNegocioException("ativo e obrigatorio.");
        }
        Usuario atualizado = escrita.execute(status -> {
            Usuario motorista = buscarMotorista(id);
            motorista.definirAtivo(ativo);
            if (!ativo) {
                motorista.revogarSessoesAnteriores();
            }
            return usuarioRepository.save(motorista);
        });
        sessaoUsuarioService.esquecer(id);
        return atualizado;
    }

    /** O administrador define a nova senha (nao ha "esqueci minha senha" ainda); a conta segue com o mesmo login. */
    public void redefinirSenha(UUID id, String novaSenha) {
        validarSenha(novaSenha);
        escrita.executeWithoutResult(status -> {
            Usuario motorista = buscarMotorista(id);
            motorista.definirSenhaHash(passwordEncoder.encode(novaSenha));
            motorista.revogarSessoesAnteriores();
            usuarioRepository.save(motorista);
        });
        sessaoUsuarioService.esquecer(id);
    }

    /** So o nome muda; o login e a identidade da conta (o indice unico e o historico dependem dele). */
    public Usuario renomear(UUID id, String novoNome) {
        if (novoNome == null || novoNome.isBlank()) {
            throw new RegraNegocioException("nome e obrigatorio.");
        }
        return escrita.execute(status -> {
            Usuario motorista = buscarMotorista(id);
            motorista.definirNome(novoNome.trim());
            return usuarioRepository.save(motorista);
        });
    }

    /** So enxerga motoristas do proprio tenant (RLS) e nunca administradores: outro papel responde 404 como se nao existisse. */
    private Usuario buscarMotorista(UUID id) {
        return usuarioRepository.findById(id)
                .filter(usuario -> Papeis.MOTORISTA.equals(usuario.getPapel()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motorista nao encontrado."));
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("senha e obrigatoria e deve ter ao menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}
