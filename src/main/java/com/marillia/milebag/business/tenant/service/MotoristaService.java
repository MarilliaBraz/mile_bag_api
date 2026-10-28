package com.marillia.milebag.business.tenant.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

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

    public MotoristaService(
            UsuarioRepository usuarioRepository, AutenticacaoService autenticacaoService, PasswordEncoder passwordEncoder,
            SessaoUsuarioService sessaoUsuarioService, RedefinicaoSenhaRepository redefinicaoSenhaRepository,
            PlatformTransactionManager transactionManager) {
        super(usuarioRepository, autenticacaoService, passwordEncoder, sessaoUsuarioService, redefinicaoSenhaRepository, transactionManager,
                Papeis.MOTORISTA, "motorista", "motoristas");
    }
}
