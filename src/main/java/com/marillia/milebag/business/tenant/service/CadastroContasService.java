package com.marillia.milebag.business.tenant.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.RedefinicaoSenhaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Regras comuns do cadastro de contas operacionais pelo administrador do tenant: motoristas das empresas de entrega
 * (HU23, HU26, HU27) e funcionarios das companhias aereas (HU33). Cada especializacao so define o papel e os rotulos
 * das mensagens; login unico no sistema, senha minima, lote e desativacao sao os mesmos.
 *
 * <p>Sem {@code @Transactional} de proposito: a checagem de login unico percorre
 * os tenants e nao pode rodar dentro da transacao da escrita (ver
 * {@link AutenticacaoService#loginJaExiste}); cada escrita abre a sua.</p>
 */
public abstract class CadastroContasService {

    static final int TAMANHO_MINIMO_SENHA = 8;
    /** Cada cadastro faz um hash BCrypt (~100 ms): o teto mantem o pedido dentro de poucos segundos. */
    static final int MAXIMO_POR_LOTE = 100;
    /** Sem caracteres que se confundem ao ditar/copiar (0/O, 1/l/I). */
    private static final String ALFABETO_SENHA = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_SENHA_GERADA = 12;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final AutenticacaoService autenticacaoService;
    private final PasswordEncoder passwordEncoder;
    private final SessaoUsuarioService sessaoUsuarioService;
    private final RedefinicaoSenhaRepository redefinicaoSenhaRepository;
    private final TransactionTemplate escrita;
    private final TransactionTemplate leitura;
    private final String papel;
    /** Rotulos das mensagens: "motorista"/"motoristas", "funcionario"/"funcionarios". */
    private final String rotulo;
    private final String rotuloPlural;

    protected CadastroContasService(
            UsuarioRepository usuarioRepository, AutenticacaoService autenticacaoService, PasswordEncoder passwordEncoder,
            SessaoUsuarioService sessaoUsuarioService, RedefinicaoSenhaRepository redefinicaoSenhaRepository,
            PlatformTransactionManager transactionManager, String papel, String rotulo, String rotuloPlural) {
        this.papel = papel;
        this.rotulo = rotulo;
        this.rotuloPlural = rotuloPlural;
        this.sessaoUsuarioService = sessaoUsuarioService;
        this.redefinicaoSenhaRepository = redefinicaoSenhaRepository;
        this.usuarioRepository = usuarioRepository;
        this.autenticacaoService = autenticacaoService;
        this.passwordEncoder = passwordEncoder;
        this.escrita = new TransactionTemplate(transactionManager);
        this.leitura = new TransactionTemplate(transactionManager);
        this.leitura.setReadOnly(true);
    }

    public List<Usuario> listar() {
        return leitura.execute(status -> usuarioRepository.findByPapelOrderByNomeAscEmailAsc(papel));
    }

    /** Uma linha do lote (HU27): {@code senha} vazia pede uma senha gerada. */
    public record NovaConta(String nome, String login, String senha) {
    }

    /** Resultado de uma linha do lote; {@code senhaGerada} so vem quando o sistema gerou a senha (nao ha como ler depois). */
    public record ResultadoLote(int linha, String nome, String login, boolean criado, String mensagem, Usuario conta, String senhaGerada) {
    }

    /**
     * Cadastra varias contas de uma vez (HU27). Cada linha e independente: uma que falha (login repetido, senha curta...) nao
     * impede as outras, e o resultado diz o que aconteceu com cada uma. Um login repetido DENTRO do lote conta como erro na 2a ocorrencia.
     */
    public List<ResultadoLote> criarEmLote(List<NovaConta> linhas) {
        if (linhas == null || linhas.isEmpty()) {
            throw new RegraNegocioException("informe ao menos um " + rotulo + ".");
        }
        if (linhas.size() > MAXIMO_POR_LOTE) {
            throw new RegraNegocioException("o lote aceita no maximo " + MAXIMO_POR_LOTE + " " + rotuloPlural + " por vez.");
        }
        java.util.Set<String> vistos = new java.util.HashSet<>();
        List<ResultadoLote> resultados = new java.util.ArrayList<>();
        int numero = 0;
        for (NovaConta linha : linhas) {
            numero++;
            String loginLimpo = linha.login() == null ? "" : linha.login().trim();
            if (!loginLimpo.isEmpty() && !vistos.add(loginLimpo.toLowerCase(java.util.Locale.ROOT))) {
                resultados.add(new ResultadoLote(numero, linha.nome(), loginLimpo, false, "login repetido neste lote", null, null));
                continue;
            }
            boolean gerar = linha.senha() == null || linha.senha().isEmpty();
            String senha = gerar ? gerarSenha() : linha.senha();
            try {
                Usuario criado = criar(linha.nome(), linha.login(), senha);
                resultados.add(new ResultadoLote(numero, criado.getNome(), criado.getEmail(), true, null, criado, gerar ? senha : null));
            } catch (RegraNegocioException | ConflitoException e) {
                resultados.add(new ResultadoLote(numero, linha.nome(), loginLimpo, false, e.getMessage(), null, null));
            }
        }
        return resultados;
    }

    private static String gerarSenha() {
        StringBuilder senha = new StringBuilder(TAMANHO_SENHA_GERADA);
        for (int i = 0; i < TAMANHO_SENHA_GERADA; i++) {
            senha.append(ALFABETO_SENHA.charAt(ALEATORIO.nextInt(ALFABETO_SENHA.length())));
        }
        return senha.toString();
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
            return escrita.execute(status -> usuarioRepository.save(new Usuario(loginLimpo, passwordEncoder.encode(senha), papel, nome.trim())));
        } catch (DataIntegrityViolationException e) {
            // Corrida entre dois cadastros com o mesmo login: quem perde e barrado pelo indice unico (V29).
            throw new ConflitoException("Ja existe uma conta com este login.");
        }
    }

    /** PATCH parcial: aplica o nome, o login e/ou o estado informados (ao menos um). */
    public Usuario atualizar(UUID id, String nome, String login, Boolean ativo) {
        if (nome == null && login == null && ativo == null) {
            throw new RegraNegocioException("informe nome, login e/ou ativo.");
        }
        Usuario atual = null;
        if (nome != null) {
            atual = renomear(id, nome);
        }
        if (login != null) {
            atual = alterarLogin(id, login);
        }
        if (ativo != null) {
            atual = definirAtivo(id, ativo);
        }
        return atual;
    }

    /**
     * Troca o login (HU26). Continua unico no sistema todo (mesma checagem do cadastro, mais o indice unico de V32/V29 contra a
     * corrida). Um pedido de redefinicao de senha ainda pendente e cancelado: o link foi para o e-mail ANTIGO, e quem ainda tem
     * acesso a ele nao pode mais assumir a conta.
     */
    public Usuario alterarLogin(UUID id, String novoLogin) {
        if (novoLogin == null || novoLogin.isBlank()) {
            throw new RegraNegocioException("login e obrigatorio.");
        }
        String loginLimpo = novoLogin.trim();
        Usuario atual = leitura.execute(status -> buscarConta(id));
        // So trocar a caixa das letras (Ana -> ana) nao muda a chave do mapa: nao precisa de checagem de unicidade.
        if (!loginLimpo.equalsIgnoreCase(atual.getEmail()) && autenticacaoService.loginJaExiste(loginLimpo)) {
            throw new ConflitoException("Ja existe uma conta com este login.");
        }
        try {
            return escrita.execute(status -> {
                Usuario conta = buscarConta(id);
                conta.definirLogin(loginLimpo);
                Instant agora = Instant.now();
                redefinicaoSenhaRepository.findByUsuarioIdAndUsadoEmIsNull(id).forEach(pendente -> pendente.marcarUsada(agora));
                return usuarioRepository.save(conta);
            });
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("Ja existe uma conta com este login.");
        }
    }

    public Usuario definirAtivo(UUID id, Boolean ativo) {
        if (ativo == null) {
            throw new RegraNegocioException("ativo e obrigatorio.");
        }
        Usuario atualizado = escrita.execute(status -> {
            Usuario conta = buscarConta(id);
            conta.definirAtivo(ativo);
            if (!ativo) {
                conta.revogarSessoesAnteriores();
            }
            return usuarioRepository.save(conta);
        });
        sessaoUsuarioService.esquecer(id);
        return atualizado;
    }

    /** O administrador define a nova senha (nao ha "esqueci minha senha" ainda); a conta segue com o mesmo login. */
    public void redefinirSenha(UUID id, String novaSenha) {
        validarSenha(novaSenha);
        escrita.executeWithoutResult(status -> {
            Usuario conta = buscarConta(id);
            conta.definirSenhaHash(passwordEncoder.encode(novaSenha));
            conta.revogarSessoesAnteriores();
            usuarioRepository.save(conta);
        });
        sessaoUsuarioService.esquecer(id);
    }

    /** So o nome muda; o login e a identidade da conta (o indice unico e o historico dependem dele). */
    public Usuario renomear(UUID id, String novoNome) {
        if (novoNome == null || novoNome.isBlank()) {
            throw new RegraNegocioException("nome e obrigatorio.");
        }
        return escrita.execute(status -> {
            Usuario conta = buscarConta(id);
            conta.definirNome(novoNome.trim());
            return usuarioRepository.save(conta);
        });
    }

    /** So enxerga contas deste papel no proprio tenant (RLS) e nunca administradores: outro papel responde 404 como se nao existisse. */
    private Usuario buscarConta(UUID id) {
        return usuarioRepository.findById(id)
                .filter(usuario -> papel.equals(usuario.getPapel()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(Character.toUpperCase(rotulo.charAt(0)) + rotulo.substring(1) + " nao encontrado."));
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("senha e obrigatoria e deve ter ao menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}
