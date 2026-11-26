package com.marillia.milebag.business.tenant.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.tenant.repository.UsuarioRepository;

/**
 * V62: guarda a ultima vez que o motorista usou o app, para o painel de gestao contar quem esta online. Grava no
 * maximo uma vez por {@link #INTERVALO_GRAVACAO} por conta (o app consulta a API a cada poucos segundos). Uma falha
 * aqui nunca derruba a requisicao: so o "online" fica desatualizado.
 */
@Service
public class UltimoAcessoService {

    static final Duration INTERVALO_GRAVACAO = Duration.ofMinutes(1);
    private static final Logger log = LoggerFactory.getLogger(UltimoAcessoService.class);

    private final UsuarioRepository usuarioRepository;
    private final TransactionTemplate escrita;
    private final Map<UUID, Instant> gravadoEm = new ConcurrentHashMap<>();

    public UltimoAcessoService(UsuarioRepository usuarioRepository, PlatformTransactionManager transactionManager) {
        this.usuarioRepository = usuarioRepository;
        this.escrita = new TransactionTemplate(transactionManager);
    }

    public void registrar(UUID usuarioId) {
        Instant agora = Instant.now();
        Instant anterior = gravadoEm.get(usuarioId);
        if (anterior != null && agora.isBefore(anterior.plus(INTERVALO_GRAVACAO))) {
            return;
        }
        gravadoEm.put(usuarioId, agora);
        try {
            escrita.executeWithoutResult(status -> usuarioRepository.registrarAcesso(usuarioId, agora));
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel registrar o ultimo acesso de {}.", usuarioId, e);
        }
    }
}
