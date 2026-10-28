package com.marillia.milebag.business.tenant.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import com.marillia.milebag.business.tenant.repository.RedefinicaoSenhaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.Papeis;

/**
 * Cadastro dos funcionarios de uma companhia aerea pelo administrador dela (HU33, RD19): as mesmas regras do cadastro
 * de motoristas ({@link CadastroContasService}), com o papel {@code FUNCIONARIO_COMPANHIA}.
 */
@Service
public class FuncionarioCompanhiaService extends CadastroContasService {

    public FuncionarioCompanhiaService(
            UsuarioRepository usuarioRepository, AutenticacaoService autenticacaoService, PasswordEncoder passwordEncoder,
            SessaoUsuarioService sessaoUsuarioService, RedefinicaoSenhaRepository redefinicaoSenhaRepository,
            PlatformTransactionManager transactionManager) {
        super(usuarioRepository, autenticacaoService, passwordEncoder, sessaoUsuarioService, redefinicaoSenhaRepository, transactionManager,
                Papeis.FUNCIONARIO_COMPANHIA, "funcionario", "funcionarios");
    }
}
