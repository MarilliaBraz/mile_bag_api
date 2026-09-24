package com.marillia.milebag.business.tenant.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.config.security.SessaoValidador;

/**
 * Recusa sessoes de contas desativadas ou de tokens emitidos antes de uma revogacao (HU23). Sem isto, um
 * motorista desativado seguiria com o app aberto ate o JWT expirar.
 *
 * <p>Custo: uma leitura de {@code usuario} por conta a cada {@link #VALIDADE_DO_CACHE}, nao por requisicao. Por
 * isso uma desativacao feita em OUTRA instancia da API leva ate esse tempo para valer nela; na instancia que
 * faz a alteracao vale na hora ({@link #esquecer}). O administrador da plataforma (sem tenant, sem conta em
 * {@code usuario}) nao passa por esta checagem.</p>
 */
@Service
public class SessaoUsuarioService implements SessaoValidador {

    static final Duration VALIDADE_DO_CACHE = Duration.ofSeconds(15);

    private record Situacao(boolean ativo, Instant tokensValidosDesde, Instant verificadoEm) {
    }

    private final UsuarioRepository usuarioRepository;
    private final TransactionTemplate leitura;
    private final Map<UUID, Situacao> cache = new ConcurrentHashMap<>();

    public SessaoUsuarioService(UsuarioRepository usuarioRepository, PlatformTransactionManager transactionManager) {
        this.usuarioRepository = usuarioRepository;
        this.leitura = new TransactionTemplate(transactionManager);
        this.leitura.setReadOnly(true);
    }

    @Override
    public boolean sessaoValida(AuthenticatedUser usuario) {
        if (usuario.tenantId() == null || Papeis.ADMINISTRADOR_PLATAFORMA.equals(usuario.papel())) {
            return true;
        }
        Situacao situacao = cache.get(usuario.userId());
        if (situacao == null || Instant.now().isAfter(situacao.verificadoEm().plus(VALIDADE_DO_CACHE))) {
            situacao = carregar(usuario.userId());
            cache.put(usuario.userId(), situacao);
        }
        if (!situacao.ativo()) {
            return false;
        }
        if (situacao.tokensValidosDesde() == null || usuario.emitidoEm() == null) {
            return true;
        }
        // O iat do JWT tem precisao de segundos; sem truncar, um login feito no mesmo segundo da revogacao seria recusado.
        return !usuario.emitidoEm().isBefore(situacao.tokensValidosDesde().truncatedTo(ChronoUnit.SECONDS));
    }

    /** Descarta a copia em memoria de uma conta que acabou de mudar (desativacao, senha), para valer na hora. */
    public void esquecer(UUID usuarioId) {
        cache.remove(usuarioId);
    }

    private Situacao carregar(UUID usuarioId) {
        Usuario conta = leitura.execute(status -> usuarioRepository.findById(usuarioId).orElse(null));
        // Conta inexistente (token assinado para um id que nao esta no banco) nao e sessao valida.
        return conta == null
                ? new Situacao(false, null, Instant.now())
                : new Situacao(conta.isAtivo(), conta.getTokensValidosDesde(), Instant.now());
    }
}
