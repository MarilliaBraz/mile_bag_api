package com.marillia.milebag.business.comprovante.service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.correcao.service.DescricaoCorrecao;
import com.marillia.milebag.business.correcao.service.CorrecaoEntregaService;
import com.marillia.milebag.business.correcao.model.MomentoCorrecao;
import com.marillia.milebag.business.correcao.model.EtapaCorrecao;
import com.marillia.milebag.business.correcao.model.CorrecaoInformada;
import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.repository.ComprovanteEntregaRepository;
import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.fotoentrega.model.TipoFotoEntrega;
import com.marillia.milebag.business.fotoentrega.service.FotoEntregaService;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.business.viagem.model.EncerrarTrajetoRequest;
import com.marillia.milebag.business.viagem.model.RegistrarCheckpointRequest;
import com.marillia.milebag.business.viagem.model.TipoCheckpoint;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.ComprovanteIncompletoException;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.util.TextoLivre;

/**
 * Registra o comprovante de entrega (POD) e encerra a entrega (US1 desta
 * HU) - ver contracts/comprovante-entrega-api.md.
 */
@Service
public class ComprovanteEntregaService {

    private static final Logger log = LoggerFactory.getLogger(ComprovanteEntregaService.class);

    private final ComprovanteEntregaRepository comprovanteEntregaRepository;
    private final EntregaService entregaService;
    private final ViagemService viagemService;
    private final MensagemService mensagemService;
    private final FotoEntregaService fotoEntregaService;
    private final MotivoOcorrenciaService motivoOcorrenciaService;
    private final CorrecaoEntregaService correcaoEntregaService;
    /** V48 (R): ate quando o comprovante sem os dados novos (app antigo ou fila offline de antes) ainda e aceito. */
    private final Instant formatoAntigoAceitoAte;

    static final int TAMANHO_MAXIMO_NOME = 200;

    public ComprovanteEntregaService(
            ComprovanteEntregaRepository comprovanteEntregaRepository,
            EntregaService entregaService,
            ViagemService viagemService,
            MensagemService mensagemService,
            FotoEntregaService fotoEntregaService,
            MotivoOcorrenciaService motivoOcorrenciaService,
            CorrecaoEntregaService correcaoEntregaService,
            @Value("${milebag.comprovante.formato-antigo-aceito-ate:2026-12-01T03:00:00Z}") Instant formatoAntigoAceitoAte) {
        this.comprovanteEntregaRepository = comprovanteEntregaRepository;
        this.entregaService = entregaService;
        this.viagemService = viagemService;
        this.mensagemService = mensagemService;
        this.fotoEntregaService = fotoEntregaService;
        this.motivoOcorrenciaService = motivoOcorrenciaService;
        this.correcaoEntregaService = correcaoEntregaService;
        this.formatoAntigoAceitoAte = formatoAntigoAceitoAte;
    }

    /**
     * V48 (itens 4 e 5): o que a entrega passou a pedir alem da foto do BDO assinado - a foto da bagagem na porta, quem
     * assinou (lista da empresa) e o nome, e ate 5 fotos de avaria com uma observacao. Tudo vazio = formato antigo.
     */
    public record DadosDaEntrega(
            MultipartFile fotoPorta, UUID tipoRecebedorId, String nomeRecebedor, List<MultipartFile> avarias, String observacaoAvaria,
            List<CorrecaoInformada> correcoes) {

        public static final DadosDaEntrega FORMATO_ANTIGO = new DadosDaEntrega(null, null, null, List.of(), null);

        /** Sem correcoes feitas no aparelho (o caso comum, e o de aparelhos anteriores a V51). */
        public DadosDaEntrega(MultipartFile fotoPorta, UUID tipoRecebedorId, String nomeRecebedor, List<MultipartFile> avarias,
                String observacaoAvaria) {
            this(fotoPorta, tipoRecebedorId, nomeRecebedor, avarias, observacaoAvaria, List.of());
        }

        boolean formatoAntigo() {
            return (fotoPorta == null || fotoPorta.isEmpty()) && tipoRecebedorId == null && (nomeRecebedor == null || nomeRecebedor.isBlank());
        }
    }

    /** Ponto de leitura publico para outros dominios (fechamento) - nunca {@code ComprovanteEntregaRepository} direto (CONVENTIONS.md 2.1). */
    @Transactional(readOnly = true)
    public List<ComprovanteEntrega> listarPorPeriodo(Instant inicio, Instant fim) {
        return comprovanteEntregaRepository.findByDataHoraBetween(inicio, fim);
    }

    /** Ponto de leitura publico para a rastreabilidade da entrega (V46). */
    @Transactional(readOnly = true)
    public Optional<ComprovanteEntrega> buscarPorEntrega(UUID entregaId) {
        return comprovanteEntregaRepository.findByEntregaId(entregaId);
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
            String justificativaDivergencia,
            DadosDaEntrega dadosDaEntrega) {
        DadosDaEntrega dados = dadosDaEntrega == null ? DadosDaEntrega.FORMATO_ANTIGO : dadosDaEntrega;

        // V47: a entrega cancelada pela empresa para antes de gravar a foto (marcarEncerradaComSucesso tambem recusa).
        EntregaService.exigirNaoCancelada(entregaService.buscarPorId(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada.")));

        // Antes de validar: o reenvio do mesmo registro (fila offline) devolve o que ja foi gravado, mesmo que algo
        // tenha mudado depois (ex.: o tipo de quem assinou foi desativado).
        Optional<ComprovanteEntrega> existente = comprovanteEntregaRepository.findByEntregaId(entregaId);
        if (existente.isPresent()) {
            if (idLocal != null && idLocal.equals(existente.get().getIdLocal())) {
                log.info("Comprovante da entrega {} ja registrado com o mesmo idLocal - retorno idempotente.", entregaId);
                return new ComprovanteEntregaResultado(existente.get(), false);
            }
            throw new ConflitoException("Esta entrega ja possui um comprovante registrado.");
        }

        DadosValidados validados = validarDadosDaEntrega(dados, identificacaoRecebedor);
        Instant dataHora = validarCampos(foto, validados.identificacao(), dataHoraTexto, latitude, longitude);

        String caminhoFoto = fotoEntregaService.gravarArquivo(foto);
        boolean registradoOffline = idLocal != null && !idLocal.isBlank();

        ComprovanteEntrega novo = new ComprovanteEntrega(
                entregaId, caminhoFoto, validados.identificacao(), dataHora, latitude, longitude, registradoOffline, idLocal);
        novo.registrarDadosDaEntrega(validados.tipoRecebedorId(), validados.nome(), validados.observacao());
        ComprovanteEntrega comprovante = comprovanteEntregaRepository.save(novo);
        // V51: correcoes feitas no aparelho antes de finalizar (voltar a uma etapa ja concluida), com motivo.
        Entrega entrega = entregaService.buscarPorId(entregaId).orElseThrow();
        if (entrega.getMotoristaResponsavel() != null) {
            correcaoEntregaService.registrarDoFluxo(entregaId, entrega.getMotoristaResponsavel(), dados.correcoes(), dataHora);
        } else if (!dados.correcoes().isEmpty()) {
            log.warn("Entrega {} sem motorista responsavel: {} correcoes do fluxo nao registradas.", entregaId, dados.correcoes().size());
        }
        if (!dados.formatoAntigo()) {
            fotoEntregaService.salvarDoComprovante(entregaId, comprovante.getId(), TipoFotoEntrega.PORTA, dados.fotoPorta(), dataHora);
            validados.avarias().forEach(avaria ->
                    fotoEntregaService.salvarDoComprovante(entregaId, comprovante.getId(), TipoFotoEntrega.AVARIA_ENTREGA, avaria, dataHora));
        }

        // data-model.md, "Fluxo de consolidacao": o comprovante cria o
        // CheckpointGps final e, em seguida, aciona a consolidacao do
        // trajeto (HU04) - dentro da mesma transacao, para que uma
        // divergencia sem justificativa desfaca tudo e permita reenvio.
        viagemService.registrarCheckpoint(entregaId, new RegistrarCheckpointRequest(TipoCheckpoint.COMPROVANTE, latitude, longitude));
        viagemService.encerrarTrajeto(entregaId, new EncerrarTrajetoRequest(justificativaDivergencia));

        entregaService.marcarEncerradaComSucesso(entregaId);

        log.info("Comprovante {} registrado; entrega {} encerrada com sucesso.", comprovante.getId(), entregaId);
        notificarAutomaticaComSeguranca(entregaId, TipoEventoNotificacao.COMPROVANTE_REGISTRADO);
        return new ComprovanteEntregaResultado(comprovante, true);
    }

    /** Prazo para o motorista corrigir a entrega finalizada (V51, X2): 24 h depois de registrada no servidor. */
    public static final Duration PRAZO_CORRECAO = Duration.ofHours(24);

    /** Ate quando o motorista ainda pode corrigir esta entrega - vazio se nao tem comprovante. */
    @Transactional(readOnly = true)
    public Optional<Instant> corrigivelAte(UUID entregaId) {
        return comprovanteEntregaRepository.findByEntregaId(entregaId).map(c -> c.getCriadoEm().plus(PRAZO_CORRECAO));
    }

    /**
     * Correcao da entrega finalizada pelo motorista responsavel, ate 24 h depois (V51): fotos (porta, BDO assinado,
     * avarias) e quem assinou, sempre com motivo. Nenhuma foto e apagada - a anterior fica como substituida - e cada
     * mudanca vira um registro de correcao com antes e depois. Precisa de internet.
     */
    @Transactional
    public ComprovanteEntrega corrigir(UUID entregaId, UUID motoristaId, CorrecaoDaEntrega correcao) {
        // Travada: dois envios da mesma correcao (toque duplo, reenvio) nao substituem as mesmas fotos duas vezes.
        Entrega entrega = entregaService.travarParaCorrigir(entregaId);
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new ConflitoException("So o motorista que fez a entrega pode corrigi-la.");
        }
        if (entrega.getStatus() != EntregaStatus.ENCERRADA_COM_SUCESSO) {
            throw new ConflitoException("So a entrega finalizada pode ser corrigida por aqui.");
        }
        ComprovanteEntrega comprovante = comprovanteEntregaRepository.findByEntregaId(entregaId)
                .orElseThrow(() -> new ConflitoException("Esta entrega nao tem comprovante para corrigir."));
        Instant agora = Instant.now();
        if (agora.isAfter(comprovante.getCriadoEm().plus(PRAZO_CORRECAO))) {
            throw new ConflitoException("O prazo de 24 horas para corrigir esta entrega terminou.");
        }
        boolean trocaPorta = correcao.fotoPorta() != null && !correcao.fotoPorta().isEmpty();
        boolean trocaDocumento = correcao.fotoDocumento() != null && !correcao.fotoDocumento().isEmpty();
        boolean trocaRecebedor = correcao.tipoRecebedorId() != null || (correcao.nomeRecebedor() != null && !correcao.nomeRecebedor().isBlank());
        if (!trocaPorta && !trocaDocumento && !trocaRecebedor && !correcao.corrigirAvarias()) {
            throw new RegraNegocioException("Informe o que corrigir.");
        }
        correcaoEntregaService.exigirMotivo(correcao.motivoId(), correcao.complemento());
        UUID motivo = correcao.motivoId();
        String complemento = correcao.complemento();

        // Validacoes antes de gravar qualquer arquivo.
        MotivoOcorrencia novoTipo = null;
        String novoNome = null;
        if (trocaRecebedor) {
            if (correcao.tipoRecebedorId() == null) {
                throw new RegraNegocioException("Informe quem assinou o recebimento.");
            }
            novoNome = TextoLivre.opcional(correcao.nomeRecebedor(), TAMANHO_MAXIMO_NOME, "nome de quem assinou");
            if (novoNome == null) {
                throw new RegraNegocioException("Informe o nome de quem assinou o recebimento.");
            }
            // Com internet: so as opcoes ativas da lista da empresa.
            novoTipo = motivoOcorrenciaService.buscarAtivoOuFalhar(correcao.tipoRecebedorId(), FinalidadeMotivo.RECEBEDOR);
        }
        List<MultipartFile> novasAvarias = correcao.corrigirAvarias() ? FotoEntregaService.avariasValidas(correcao.avarias()) : List.of();
        String novaObservacao = correcao.corrigirAvarias() ? FotoEntregaService.observacaoAvaria(correcao.observacaoAvaria()) : null;

        if (trocaPorta) {
            fotoEntregaService.substituir(entregaId, null, comprovante.getId(), TipoFotoEntrega.PORTA, List.of(correcao.fotoPorta()), agora);
            correcaoEntregaService.registrar(entregaId, EtapaCorrecao.FOTO_PORTA, MomentoCorrecao.APOS_REGISTRO, motivo, complemento,
                    "foto anterior", "foto nova", motoristaId, agora);
        }
        if (trocaDocumento) {
            String novoCaminho = fotoEntregaService.gravarArquivo(correcao.fotoDocumento());
            fotoEntregaService.guardarBdoAssinadoSubstituido(entregaId, comprovante.getId(), comprovante.getFotoDocumentoUrl(),
                    comprovante.getDataHora(), agora);
            comprovante.corrigirFotoDocumento(novoCaminho);
            correcaoEntregaService.registrar(entregaId, EtapaCorrecao.FOTO_BDO_ASSINADO, MomentoCorrecao.APOS_REGISTRO, motivo, complemento,
                    "foto anterior", "foto nova", motoristaId, agora);
        }
        if (trocaRecebedor) {
            String antes = comprovante.getIdentificacaoRecebedor();
            String identificacao = novoTipo.getDescricao() + " - " + novoNome;
            if (identificacao.length() > TAMANHO_MAXIMO_NOME) {
                identificacao = identificacao.substring(0, TAMANHO_MAXIMO_NOME);
            }
            comprovante.corrigirRecebedor(novoTipo.getId(), novoNome, identificacao);
            correcaoEntregaService.registrar(entregaId, EtapaCorrecao.RECEBEDOR, MomentoCorrecao.APOS_REGISTRO, motivo, complemento,
                    antes, identificacao, motoristaId, agora);
        }
        if (correcao.corrigirAvarias()) {
            String antes = DescricaoCorrecao.avarias(fotoEntregaService.contarAtuais(entregaId, null, comprovante.getId(), TipoFotoEntrega.AVARIA_ENTREGA),
                    comprovante.getObservacaoAvaria());
            fotoEntregaService.substituir(entregaId, null, comprovante.getId(), TipoFotoEntrega.AVARIA_ENTREGA, novasAvarias, agora);
            comprovante.corrigirObservacaoAvaria(novaObservacao);
            correcaoEntregaService.registrar(entregaId, EtapaCorrecao.AVARIAS_ENTREGA, MomentoCorrecao.APOS_REGISTRO, motivo, complemento,
                    antes, DescricaoCorrecao.avarias(novasAvarias.size(), novaObservacao), motoristaId, agora);
        }
        log.info("Entrega {} corrigida pelo motorista {} depois de finalizada.", entregaId, motoristaId);
        return comprovanteEntregaRepository.save(comprovante);
    }

    /** O que a correcao da entrega finalizada traz (V51); campos nulos ficam como estao. */
    public record CorrecaoDaEntrega(MultipartFile fotoPorta, MultipartFile fotoDocumento, UUID tipoRecebedorId, String nomeRecebedor,
            boolean corrigirAvarias, List<MultipartFile> avarias, String observacaoAvaria, UUID motivoId, String complemento) {
    }

    /**
     * Formato novo: foto na porta, quem assinou (motivo RECEBEDOR ativo) e nome obrigatorios; a identificacao do
     * recebedor passa a ser "Tipo - Nome". Formato antigo: so ate a data de corte (R), com a identificacao livre.
     */
    private DadosValidados validarDadosDaEntrega(DadosDaEntrega dados, String identificacaoAntiga) {
        if (dados.formatoAntigo()) {
            if (Instant.now().isAfter(formatoAntigoAceitoAte)) {
                throw new ComprovanteIncompletoException(
                        "Atualize o aplicativo: a entrega agora pede a foto na porta e quem assinou o recebimento.");
            }
            return new DadosValidados(identificacaoAntiga, null, null, null, List.of());
        }
        if (dados.fotoPorta() == null || dados.fotoPorta().isEmpty()) {
            throw new ComprovanteIncompletoException("A foto da bagagem na porta e obrigatoria.");
        }
        if (dados.tipoRecebedorId() == null) {
            throw new ComprovanteIncompletoException("Informe quem assinou o recebimento.");
        }
        String nome = TextoLivre.opcional(dados.nomeRecebedor(), TAMANHO_MAXIMO_NOME, "nome de quem assinou");
        if (nome == null) {
            throw new ComprovanteIncompletoException("Informe o nome de quem assinou o recebimento.");
        }
        // Aceita um tipo desativado depois: o motorista escolheu da lista que tinha (inclusive sem internet).
        MotivoOcorrencia tipo = motivoOcorrenciaService.buscarDaFinalidadeOuFalhar(dados.tipoRecebedorId(), FinalidadeMotivo.RECEBEDOR);
        List<MultipartFile> avarias = FotoEntregaService.avariasValidas(dados.avarias());
        String observacao = FotoEntregaService.observacaoAvaria(dados.observacaoAvaria());
        String identificacao = tipo.getDescricao() + " - " + nome;
        if (identificacao.length() > TAMANHO_MAXIMO_NOME) {
            identificacao = identificacao.substring(0, TAMANHO_MAXIMO_NOME);
        }
        return new DadosValidados(identificacao, tipo.getId(), nome, observacao, avarias);
    }

    private record DadosValidados(String identificacao, UUID tipoRecebedorId, String nome, String observacao, List<MultipartFile> avarias) {
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

    /** FR-005/FR-005 (HU18/HU20): notificacao automatica nunca bloqueia nem falha o registro do comprovante. */
    private void notificarAutomaticaComSeguranca(UUID entregaId, TipoEventoNotificacao tipoEvento) {
        try {
            mensagemService.notificarAutomatica(entregaId, tipoEvento);
        } catch (RuntimeException e) {
            log.warn("Falha ao notificar automaticamente a entrega {} do evento {} - comprovante ja registrado, seguindo.", entregaId, tipoEvento, e);
        }
    }

    /** {@code novo}: distingue um registro recem-criado de um retorno idempotente, para o controller escolher o status HTTP. */
    public record ComprovanteEntregaResultado(ComprovanteEntrega comprovante, boolean novo) {
    }
}
