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

    /** V58: se a empresa oculta deste motorista a tarifa paga por viagem. Conta inexistente: a tarifa aparece. */
    @Transactional(readOnly = true)
    public boolean ocultaTarifa(UUID motoristaId) {
        return usuarioRepository.findById(motoristaId).map(Usuario::isOcultarTarifa).orElse(false);
    }

    /** V52: a base do motorista (vazio se ele ainda nao tem). */
    @Transactional(readOnly = true)
    public Optional<UUID> baseDoMotorista(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId).map(Usuario::getBaseId);
    }

    /** V52: so os motoristas ativos de uma base - quem recebe o aviso de BDO novo daquela base. */
    @Transactional(readOnly = true)
    public List<UUID> idsDosMotoristasAtivosDaBase(UUID baseId) {
        return usuarioRepository.findByPapelOrderByNomeAscEmailAsc(Papeis.MOTORISTA).stream()
                .filter(Usuario::isAtivo)
                .filter(usuario -> baseId.equals(usuario.getBaseId()))
                .map(Usuario::getId)
                .toList();
    }

    /** HU32: motoristas ativos do tenant (todas as bases). */
    @Transactional(readOnly = true)
    public List<UUID> idsDosMotoristasAtivos() {
        return usuarioRepository.findByPapelOrderByNomeAscEmailAsc(Papeis.MOTORISTA).stream()
                .filter(Usuario::isAtivo)
                .map(Usuario::getId)
                .toList();
    }
}
