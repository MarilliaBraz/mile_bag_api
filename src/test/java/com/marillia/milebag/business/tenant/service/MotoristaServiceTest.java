package com.marillia.milebag.business.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.RedefinicaoSenhaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class MotoristaServiceTest {

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

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private MotoristaService service;

    @BeforeEach
    void setUp() {
        service = new MotoristaService(usuarioRepository, autenticacaoService, passwordEncoder, sessaoUsuarioService, redefinicaoSenhaRepository, transactionManager);
    }

    @Test
    void criaMotoristaComSenhaEmHashENomeSemEspacos() {
        when(autenticacaoService.loginJaExiste("joao@empresa.com")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Usuario criado = service.criar("  Joao Silva ", " joao@empresa.com ", "senha-forte-1");

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(salvo.capture());
        assertThat(criado).isSameAs(salvo.getValue());
        assertThat(criado.getPapel()).isEqualTo(Papeis.MOTORISTA);
        assertThat(criado.getNome()).isEqualTo("Joao Silva");
        assertThat(criado.getEmail()).isEqualTo("joao@empresa.com");
        assertThat(criado.isAtivo()).isTrue();
        assertThat(criado.getSenhaHash()).isNotEqualTo("senha-forte-1");
        assertThat(passwordEncoder.matches("senha-forte-1", criado.getSenhaHash())).isTrue();
    }

    @Test
    void loginJaExistenteEmQualquerEmpresaEConflito() {
        when(autenticacaoService.loginJaExiste("joao")).thenReturn(true);

        assertThatThrownBy(() -> service.criar("Joao", "joao", "senha-forte-1")).isInstanceOf(ConflitoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void recusaNomeLoginOuSenhaInvalidosSemConsultarNada() {
        assertThatThrownBy(() -> service.criar(" ", "joao", "senha-forte-1")).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criar("Joao", "", "senha-forte-1")).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criar("Joao", "joao", "curta")).isInstanceOf(RegraNegocioException.class);
        verify(autenticacaoService, never()).loginJaExiste(any());
    }

    @Test
    void desativaEReativaMotorista() {
        Usuario motorista = motorista();
        when(usuarioRepository.findById(motorista.getId())).thenReturn(Optional.of(motorista));
        when(usuarioRepository.save(motorista)).thenReturn(motorista);

        assertThat(service.definirAtivo(motorista.getId(), false).isAtivo()).isFalse();
        assertThat(service.definirAtivo(motorista.getId(), true).isAtivo()).isTrue();
    }

    @Test
    void administradorNaoPodeSerGerenciadoPorAquiEResponde404() {
        Usuario admin = new Usuario("admin@empresa.com", "hash", Papeis.ADMINISTRADOR_TENANT);
        admin.setId(UUID.randomUUID());
        when(usuarioRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.definirAtivo(admin.getId(), false)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.redefinirSenha(admin.getId(), "nova-senha-1")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(admin.isAtivo()).isTrue();
    }

    @Test
    void redefinirSenhaTrocaOHashEValidaOTamanho() {
        Usuario motorista = motorista();
        String hashAntigo = motorista.getSenhaHash();
        when(usuarioRepository.findById(motorista.getId())).thenReturn(Optional.of(motorista));

        service.redefinirSenha(motorista.getId(), "outra-senha-9");

        assertThat(motorista.getSenhaHash()).isNotEqualTo(hashAntigo);
        assertThat(passwordEncoder.matches("outra-senha-9", motorista.getSenhaHash())).isTrue();
        assertThatThrownBy(() -> service.redefinirSenha(motorista.getId(), "curta")).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void desativarRevogaSessoesAnterioresEEsqueceOCacheDeSessao() {
        Usuario motorista = motorista();
        when(usuarioRepository.findById(motorista.getId())).thenReturn(Optional.of(motorista));
        when(usuarioRepository.save(motorista)).thenReturn(motorista);

        service.definirAtivo(motorista.getId(), false);

        assertThat(motorista.getTokensValidosDesde()).isNotNull();
        verify(sessaoUsuarioService).esquecer(motorista.getId());
    }

    @Test
    void reativarNaoRevogaMaisNada() {
        Usuario motorista = motorista();
        when(usuarioRepository.findById(motorista.getId())).thenReturn(Optional.of(motorista));
        when(usuarioRepository.save(motorista)).thenReturn(motorista);

        service.definirAtivo(motorista.getId(), true);

        assertThat(motorista.getTokensValidosDesde()).isNull();
    }

    @Test
    void redefinirSenhaRevogaSessoesAnteriores() {
        Usuario motorista = motorista();
        when(usuarioRepository.findById(motorista.getId())).thenReturn(Optional.of(motorista));

        service.redefinirSenha(motorista.getId(), "outra-senha-9");

        assertThat(motorista.getTokensValidosDesde()).isNotNull();
        verify(sessaoUsuarioService).esquecer(motorista.getId());
    }

    @Test
    void atualizarAceitaSoONomeEExigeAoMenosUmCampo() {
        Usuario motorista = motorista();
        when(usuarioRepository.findById(motorista.getId())).thenReturn(Optional.of(motorista));
        when(usuarioRepository.save(motorista)).thenReturn(motorista);

        assertThat(service.atualizar(motorista.getId(), "  Joao Pedro ", null, null).getNome()).isEqualTo("Joao Pedro");
        assertThatThrownBy(() -> service.atualizar(motorista.getId(), null, null, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.atualizar(motorista.getId(), " ", null, null)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void corridaDeDoisCadastrosBarradaPeloIndiceUnicoVira409() {
        when(autenticacaoService.loginJaExiste("joao")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_usuario_email_lower"));

        assertThatThrownBy(() -> service.criar("Joao", "joao", "senha-forte-1")).isInstanceOf(ConflitoException.class);
    }

    @Test
    void trocarLoginGravaOnovoCancelaPedidosDeSenhaPendentesEChecaUnicidade() {
        Usuario motorista = motorista();
        UUID id = motorista.getId();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(motorista));
        when(usuarioRepository.save(motorista)).thenReturn(motorista);
        when(autenticacaoService.loginJaExiste("joao.novo@empresa.com")).thenReturn(false);
        com.marillia.milebag.business.tenant.model.RedefinicaoSenha pendente = new com.marillia.milebag.business.tenant.model.RedefinicaoSenha(
                UUID.randomUUID(), id, "hash", java.time.Instant.now(), java.time.Instant.now().plusSeconds(600));
        when(redefinicaoSenhaRepository.findByUsuarioIdAndUsadoEmIsNull(id)).thenReturn(java.util.List.of(pendente));

        Usuario alterado = service.alterarLogin(id, "  joao.novo@empresa.com ");

        assertThat(alterado.getEmail()).isEqualTo("joao.novo@empresa.com");
        assertThat(pendente.getUsadoEm()).as("o link foi para o e-mail antigo").isNotNull();
    }

    @Test
    void trocarLoginParaUmJaUsadoEConflitoESoMudarACaixaNaoConsultaNada() {
        Usuario motorista = motorista();
        UUID id = motorista.getId();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(motorista));
        when(autenticacaoService.loginJaExiste("outro")).thenReturn(true);

        assertThatThrownBy(() -> service.alterarLogin(id, "outro")).isInstanceOf(ConflitoException.class);
        verify(usuarioRepository, never()).save(any());

        when(usuarioRepository.save(motorista)).thenReturn(motorista);
        service.alterarLogin(id, "JOAO");
        verify(autenticacaoService, never()).loginJaExiste("JOAO");
        assertThat(motorista.getEmail()).isEqualTo("JOAO");
    }

    @Test
    void trocarLoginVazioERecusadoEACorridaNoIndiceVira409() {
        Usuario motorista = motorista();
        UUID id = motorista.getId();
        assertThatThrownBy(() -> service.alterarLogin(id, " ")).isInstanceOf(RegraNegocioException.class);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(motorista));
        when(autenticacaoService.loginJaExiste("novo")).thenReturn(false);
        when(usuarioRepository.save(motorista)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_usuario_email_lower"));

        assertThatThrownBy(() -> service.alterarLogin(id, "novo")).isInstanceOf(ConflitoException.class);
    }

    @Test
    void loteCadastraCadaLinhaPorContaPropriaGeraSenhaQuandoFaltaEContinuaAposUmErro() {
        when(autenticacaoService.loginJaExiste("ana")).thenReturn(false);
        when(autenticacaoService.loginJaExiste("bia")).thenReturn(true); // ja existe em alguma empresa
        when(autenticacaoService.loginJaExiste("caio")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> {
            Usuario u = chamada.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        var resultados = service.criarEmLote(java.util.List.of(
                new MotoristaService.NovoMotorista("Ana", "ana", null),
                new MotoristaService.NovoMotorista("Bia", "bia", "senha-forte-1"),
                new MotoristaService.NovoMotorista("Caio", "caio", "senha-forte-2")));

        assertThat(resultados).extracting(MotoristaService.ResultadoLote::criado).containsExactly(true, false, true);
        assertThat(resultados.get(0).senhaGerada()).hasSize(12).matches("[a-zA-Z2-9]+");
        assertThat(passwordEncoder.matches(resultados.get(0).senhaGerada(), resultados.get(0).motorista().getSenhaHash())).isTrue();
        assertThat(resultados.get(1).mensagem()).contains("Ja existe");
        assertThat(resultados.get(2).senhaGerada()).as("senha informada nao volta na resposta").isNull();
    }

    @Test
    void loteMarcaLoginRepetidoDentroDoProprioLoteEValidaCadaLinha() {
        when(autenticacaoService.loginJaExiste("ana")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var resultados = service.criarEmLote(java.util.List.of(
                new MotoristaService.NovoMotorista("Ana", "ana", "senha-forte-1"),
                new MotoristaService.NovoMotorista("Ana 2", "ANA", "senha-forte-1"),
                new MotoristaService.NovoMotorista("", "sem-nome", "senha-forte-1"),
                new MotoristaService.NovoMotorista("Curta", "curta", "abc")));

        assertThat(resultados).extracting(MotoristaService.ResultadoLote::criado).containsExactly(true, false, false, false);
        assertThat(resultados.get(1).mensagem()).contains("repetido neste lote");
        assertThat(resultados.get(2).mensagem()).contains("nome");
        assertThat(resultados.get(3).mensagem()).contains("senha");
        verify(usuarioRepository, org.mockito.Mockito.times(1)).save(any(Usuario.class));
    }

    @Test
    void loteVazioOuGrandeDemaisERecusado() {
        assertThatThrownBy(() -> service.criarEmLote(java.util.List.of())).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criarEmLote(null)).isInstanceOf(RegraNegocioException.class);
        var demais = java.util.Collections.nCopies(MotoristaService.MAXIMO_POR_LOTE + 1, new MotoristaService.NovoMotorista("X", "x", "senha-forte-1"));
        assertThatThrownBy(() -> service.criarEmLote(demais)).isInstanceOf(RegraNegocioException.class);
    }

    private Usuario motorista() {
        Usuario motorista = new Usuario("joao", passwordEncoder.encode("senha-forte-1"), Papeis.MOTORISTA, "Joao");
        motorista.setId(UUID.randomUUID());
        return motorista;
    }
}
