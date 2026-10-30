package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.RedefinicaoSenhaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** HU33: as regras sao as de {@link CadastroContasService} (cobertas em MotoristaServiceTest); aqui, o que muda pelo papel. */
@ExtendWith(MockitoExtension.class)
class FuncionarioCompanhiaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AutenticacaoService autenticacaoService;
    @Mock
    private SessaoUsuarioService sessaoUsuarioService;
    @Mock
    private RedefinicaoSenhaRepository redefinicaoSenhaRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private FuncionarioCompanhiaService service;

    @BeforeEach
    void setUp() {
        service = new FuncionarioCompanhiaService(
                usuarioRepository, autenticacaoService, new BCryptPasswordEncoder(), sessaoUsuarioService, redefinicaoSenhaRepository, transactionManager);
    }

    @Test
    void criaComOPapelDeFuncionarioDaCompanhia() {
        when(autenticacaoService.loginJaExiste("rita")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        assertThat(service.criar("Rita", "rita", "senha-forte-1").getPapel()).isEqualTo(Papeis.FUNCIONARIO_COMPANHIA);
    }

    @Test
    void listaSoFuncionarios() {
        when(usuarioRepository.findByPapelOrderByNomeAscEmailAsc(Papeis.FUNCIONARIO_COMPANHIA)).thenReturn(List.of());

        assertThat(service.listar()).isEmpty();
    }

    /** Um motorista (ou o administrador) nao e funcionario: responde 404 como se nao existisse. */
    @Test
    void contaDeOutroPapelNaoEEncontrada() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(new Usuario("joao", "hash", Papeis.MOTORISTA, "Joao")));

        assertThatThrownBy(() -> service.renomear(id, "Outro"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Funcionario nao encontrado.");
    }

    @Test
    void mensagensDoLoteFalamDeFuncionarios() {
        assertThatThrownBy(() -> service.criarEmLote(List.of()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("informe ao menos um funcionario.");
    }
}
