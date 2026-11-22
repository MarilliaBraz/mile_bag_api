package com.marillia.milebag.business.tenant.service;

import java.util.UUID;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;

import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.configuracao.service.BaseAeroportuariaService;
import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.business.tenant.repository.RedefinicaoSenhaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.Papeis;

/**
 * Cadastro de motoristas pelo administrador do tenant (HU23). Um motorista e um usuario com papel {@code MOTORISTA}: o
 * id dele e o {@code sub} do JWT, o mesmo id que as entregas, ajustes e fechamentos ja guardam. As regras ficam em
 * {@link CadastroContasService}.
 */
@Service
public class MotoristaService extends CadastroContasService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MotoristaService.class);

    private final UsuarioRepository usuarioRepository;
    private final BaseAeroportuariaService baseAeroportuariaService;

    public MotoristaService(
            UsuarioRepository usuarioRepository, AutenticacaoService autenticacaoService, PasswordEncoder passwordEncoder,
            SessaoUsuarioService sessaoUsuarioService, RedefinicaoSenhaRepository redefinicaoSenhaRepository,
            PlatformTransactionManager transactionManager, BaseAeroportuariaService baseAeroportuariaService) {
        super(usuarioRepository, autenticacaoService, passwordEncoder, sessaoUsuarioService, redefinicaoSenhaRepository, transactionManager,
                Papeis.MOTORISTA, "motorista", "motoristas");
        this.usuarioRepository = usuarioRepository;
        this.baseAeroportuariaService = baseAeroportuariaService;
    }

    /** V52: todo motorista tem base (ele so ve e so e avisado dos BDOs dela). A base e validada antes de criar a conta. */
    @Transactional
    public Usuario criarNaBase(String nome, String login, String senha, UUID baseId) {
        UUID base = exigirBase(baseId);
        return gravarBase(criar(nome, login, senha), base);
    }

    /** V52: o lote vai inteiro para a base escolhida na importacao. */
    public List<ResultadoLote> criarEmLoteNaBase(List<NovaConta> linhas, UUID baseId) {
        UUID base = exigirBase(baseId);
        List<ResultadoLote> resultados = criarEmLote(linhas);
        // Cada linha ja criada recebe a base; uma falha aqui nao derruba o resultado (com as senhas geradas) das outras.
        resultados.stream().filter(ResultadoLote::criado).forEach(r -> {
            try {
                gravarBase(r.conta(), base);
            } catch (RuntimeException e) {
                log.error("Motorista {} do lote criado sem base - defina pela edicao.", r.conta().getId(), e);
            }
        });
        return resultados;
    }

    /** V52: PATCH com base - a base e validada antes de qualquer outra alteracao, tudo numa transacao. */
    @Transactional
    public Usuario atualizarComBase(UUID id, String nome, String login, Boolean ativo, UUID baseId) {
        UUID base = baseId == null ? null : exigirBase(baseId);
        Usuario atualizado = null;
        if (nome != null || login != null || ativo != null) {
            atualizado = atualizar(id, nome, login, ativo);
        }
        if (base != null) {
            atualizado = definirBase(id, base);
        }
        if (atualizado == null) {
            throw new RegraNegocioException("informe nome, login, ativo e/ou base.");
        }
        return atualizado;
    }

    /** V52: trocar a base do motorista. */
    public Usuario definirBase(UUID id, UUID baseId) {
        UUID base = exigirBase(baseId);
        Usuario motorista = usuarioRepository.findById(id)
                .filter(usuario -> Papeis.MOTORISTA.equals(usuario.getPapel()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motorista nao encontrado."));
        return gravarBase(motorista, base);
    }

    private UUID exigirBase(UUID baseId) {
        if (baseId == null) {
            throw new RegraNegocioException("Informe a base do motorista. Cadastre as bases da empresa em Configuracoes se ainda nao houver.");
        }
        return baseAeroportuariaService.buscar(baseId)
                .map(BaseAeroportuaria::getId)
                .orElseThrow(() -> new RegraNegocioException("Base nao encontrada."));
    }

    private Usuario gravarBase(Usuario motorista, UUID base) {
        motorista.definirBase(base);
        return usuarioRepository.save(motorista);
    }
}
