package com.marillia.milebag.business.comprovante.service;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.repository.ComprovanteEntregaRepository;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.viagem.model.EncerrarTrajetoRequest;
import com.marillia.milebag.business.viagem.model.RegistrarCheckpointRequest;
import com.marillia.milebag.business.viagem.model.TipoCheckpoint;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ComprovanteIncompletoException;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.storage.ArmazenamentoImagemService;

/**
 * Registra o comprovante de entrega (POD) e encerra a entrega (US1 desta
 * HU) - ver contracts/comprovante-entrega-api.md.
 */
@Service
public class ComprovanteEntregaService {

    private static final Logger log = LoggerFactory.getLogger(ComprovanteEntregaService.class);

    private final ComprovanteEntregaRepository comprovanteEntregaRepository;
    private final EntregaService entregaService;
    private final ArmazenamentoImagemService armazenamentoImagemService;
    private final ViagemService viagemService;

    public ComprovanteEntregaService(
            ComprovanteEntregaRepository comprovanteEntregaRepository,
            EntregaService entregaService,
            ArmazenamentoImagemService armazenamentoImagemService,
            ViagemService viagemService) {
        this.comprovanteEntregaRepository = comprovanteEntregaRepository;
        this.entregaService = entregaService;
        this.armazenamentoImagemService = armazenamentoImagemService;
        this.viagemService = viagemService;
    }

    /** Ponto de leitura publico para outros dominios (fechamento) - nunca {@code ComprovanteEntregaRepository} direto (CONVENTIONS.md 2.1). */
    @Transactional(readOnly = true)
    public List<ComprovanteEntrega> listarPorPeriodo(Instant inicio, Instant fim) {
        return comprovanteEntregaRepository.findByDataHoraBetween(inicio, fim);
    }

    /**
     * {@code justificativaDivergencia} nao faz parte do contrato original
     * desta HU (anterior a regeneracao de HU04 em 2026-09-09, que introduziu
     * RN14) - adicionado aqui como campo opcional para que esta chamada
     * consiga completar o fluxo de encerramento de {@code ViagemService}
     * quando a divergencia de trajeto exigir justificativa (ver
     * contracts/viagem-api.md, "Nao e um erro fatal"): o motorista reenvia
     * este mesmo POST com o campo preenchido, sem perder o restante dos
     * dados do comprovante.
     */
    @Transactional
    public ComprovanteEntregaResultado registrar(
            UUID entregaId,
            MultipartFile foto,
            String identificacaoRecebedor,
            String dataHoraTexto,
            Double latitude,
            Double longitude,
            String idLocal,
            String justificativaDivergencia) {
        Instant dataHora = validarCampos(foto, identificacaoRecebedor, dataHoraTexto, latitude, longitude);

        if (entregaService.buscarPorId(entregaId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Entrega nao encontrada.");
        }

        Optional<ComprovanteEntrega> existente = comprovanteEntregaRepository.findByEntregaId(entregaId);
        if (existente.isPresent()) {
            if (idLocal != null && idLocal.equals(existente.get().getIdLocal())) {
                log.info("Comprovante da entrega {} ja registrado com o mesmo idLocal - retorno idempotente.", entregaId);
                return new ComprovanteEntregaResultado(existente.get(), false);
            }
            throw new ConflitoException("Esta entrega ja possui um comprovante registrado.");
        }

        String caminhoFoto = salvarFoto(TenantContext.get(), foto);
        boolean registradoOffline = idLocal != null && !idLocal.isBlank();

        ComprovanteEntrega comprovante = comprovanteEntregaRepository.save(new ComprovanteEntrega(
                entregaId, caminhoFoto, identificacaoRecebedor, dataHora, latitude, longitude, registradoOffline, idLocal));

        // data-model.md, "Fluxo de consolidacao": o comprovante cria o
        // CheckpointGps final e, em seguida, aciona a consolidacao do
        // trajeto (HU04) - dentro da mesma transacao, para que uma
        // divergencia sem justificativa desfaca tudo e permita reenvio.
        viagemService.registrarCheckpoint(entregaId, new RegistrarCheckpointRequest(TipoCheckpoint.COMPROVANTE, latitude, longitude));
        viagemService.encerrarTrajeto(entregaId, new EncerrarTrajetoRequest(justificativaDivergencia));

        entregaService.marcarEncerradaComSucesso(entregaId);

        log.info("Comprovante {} registrado; entrega {} encerrada com sucesso.", comprovante.getId(), entregaId);
        return new ComprovanteEntregaResultado(comprovante, true);
    }

    private Instant validarCampos(MultipartFile foto, String identificacaoRecebedor, String dataHoraTexto, Double latitude, Double longitude) {
        if (foto == null || foto.isEmpty()) {
            throw new ComprovanteIncompletoException("A foto do documento assinado e obrigatoria.");
        }
        if (identificacaoRecebedor == null || identificacaoRecebedor.isBlank()) {
            throw new ComprovanteIncompletoException("A identificacao do recebedor e obrigatoria.");
        }
        if (latitude == null || longitude == null) {
            throw new ComprovanteIncompletoException("As coordenadas geograficas sao obrigatorias.");
        }
        if (dataHoraTexto == null || dataHoraTexto.isBlank()) {
            throw new ComprovanteIncompletoException("A data/hora da captura e obrigatoria.");
        }
        try {
            return Instant.parse(dataHoraTexto);
        } catch (DateTimeParseException e) {
            throw new ComprovanteIncompletoException("A data/hora da captura e invalida; use o formato ISO 8601.");
        }
    }

    private String salvarFoto(UUID tenantId, MultipartFile foto) {
        try {
            return armazenamentoImagemService.salvar(tenantId, foto);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar a foto do comprovante.", e);
        }
    }

    /** {@code novo}: distingue um registro recem-criado de um retorno idempotente, para o controller escolher o status HTTP. */
    public record ComprovanteEntregaResultado(ComprovanteEntrega comprovante, boolean novo) {
    }
}
