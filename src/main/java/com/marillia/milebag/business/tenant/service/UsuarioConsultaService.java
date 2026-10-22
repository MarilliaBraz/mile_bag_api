package com.marillia.milebag.business.tenant.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.Papeis;

/**
 * Ponto de leitura de contas para outros dominios (HU30: alerta de
 * emergencia) - nunca {@code UsuarioRepository} direto de fora de
 * {@code tenant} (CONVENTIONS.md 2.1). Filtrado por tenant pela RLS.
 */
@Service
public class UsuarioConsultaService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioConsultaService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Nome da conta ou, sem nome (contas anteriores a HU23), o login. */
    @Transactional(readOnly = true)
    public Optional<String> nomeParaExibicao(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(usuario -> usuario.getNome() != null && !usuario.getNome().isBlank() ? usuario.getNome() : usuario.getEmail());
    }

    /**
     * Logins dos administradores ativos do tenant que sao enderecos de
     * e-mail - desde HU26 o login pode ser outro identificador, e esse nao
     * tem para onde enviar.
     */
    @Transactional(readOnly = true)
    public List<String> emailsDosAdministradoresAtivos() {
        return usuarioRepository.findByPapelOrderByNomeAscEmailAsc(Papeis.ADMINISTRADOR_TENANT).stream()
                .filter(Usuario::isAtivo)
                .map(Usuario::getEmail)
                .filter(login -> login != null && login.contains("@"))
                .toList();
    }
}
