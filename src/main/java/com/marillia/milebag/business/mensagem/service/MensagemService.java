package com.marillia.milebag.business.mensagem.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.mensagem.channel.EmailChannel;
import com.marillia.milebag.business.mensagem.channel.TelegramClient;
import com.marillia.milebag.business.mensagem.model.AutorMensagem;
import com.marillia.milebag.business.mensagem.model.CanalMensagem;
import com.marillia.milebag.business.mensagem.model.ContatoDisponivelResponse;
import com.marillia.milebag.business.mensagem.model.ConversaResponse;
import com.marillia.milebag.business.mensagem.model.Mensagem;
import com.marillia.milebag.business.mensagem.model.MensagemResponse;
import com.marillia.milebag.business.mensagem.model.StatusEnvioMensagem;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.model.TipoMensagem;
import com.marillia.milebag.business.mensagem.model.VinculoTelegram;
import com.marillia.milebag.business.mensagem.repository.MensagemRepository;
import com.marillia.milebag.business.mensagem.repository.VinculoTelegramRepository;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Conversa por entrega com o passageiro (HU18, US1) - ver
 * contracts/mensagens-api.md. Escolhe o canal automaticamente (FR-003):
 * e-mail sempre que capturado no BDO (RD02), Telegram assim que houver
 * {@link VinculoTelegram} - nunca exige escolha do motorista.
 *
 * <p>E tambem a implementacao real de {@code notificarAutomatica}, cuja
 * interface provisoria foi declarada por HU20 (business/viagem) enquanto
 * este dominio nao existia - ver {@code ../HU20.../tasks.md} T002/T003/T011.
 * Como esta classe nao tem nenhuma dependencia de volta a business/viagem
 * (diferente do caso de {@code SelecaoRotaProvider}), nao ha dependencia
 * circular a resolver: os consumidores passam a injetar esta classe
 * concreta diretamente, mesmo padrao ja usado para todo outro servico
 * cross-domain deste projeto (FechamentoService, PainelEntregasService).</p>
 */
@Service
public class MensagemService {

    private static final Logger log = LoggerFactory.getLogger(MensagemService.class);
    // Assunto dos e-mails da conversa; um "Re: <assunto>" sem corpo nao tem nada a registrar (EmailInboundListener).
    static final String PREFIXO_ASSUNTO_CONVERSA = "Atualizacao da sua entrega - BDO ";

    private final MensagemRepository mensagemRepository;
    private final VinculoTelegramRepository vinculoTelegramRepository;
    private final EntregaService entregaService;
    private final BdoService bdoService;
    private final TelegramClient telegramClient;
    private final EmailChannel emailChannel;
    private final ZoneId fusoHorario;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public MensagemService(
            MensagemRepository mensagemRepository,
            VinculoTelegramRepository vinculoTelegramRepository,
            EntregaService entregaService,
            BdoService bdoService,
            TelegramClient telegramClient,
            EmailChannel emailChannel,
            @Value("${milebag.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this.mensagemRepository = mensagemRepository;
        this.vinculoTelegramRepository = vinculoTelegramRepository;
        this.entregaService = entregaService;
        this.bdoService = bdoService;
        this.telegramClient = telegramClient;
        this.emailChannel = emailChannel;
        this.fusoHorario = ZoneId.of(fusoHorario);
    }

    @Transactional(readOnly = true)
    public ConversaResponse consultarConversa(UUID entregaId, UUID motoristaId) {
        Entrega entrega = buscarEntregaDoMotoristaOuFalhar(entregaId, motoristaId);
        Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());
        boolean temEmail = temEmail(bdo);
        boolean temTelegram = temEmail && vinculoTelegramRepository.existsByEntregaId(entregaId);

        // FR-006: sem e-mail, nenhum canal fica disponivel (nem Telegram, que
        // depende do convite por e-mail) - a conversa vem vazia por design,
        // nunca por uma falha de consulta.
        List<MensagemResponse> mensagens = temEmail
                ? mensagemRepository.findByEntregaIdOrderByCriadoEmAsc(entregaId).stream().map(MensagemResponse::de).toList()
                : List.of();

        return new ConversaResponse(new ContatoDisponivelResponse(temTelegram, temEmail), mensagens);
    }

    /** FR-002/FR-003/FR-007. Restricao de dono aplicada tambem aqui (nao so no GET) por consistencia com o resto do sistema, embora o contrato desta HU liste so 400/404. */
    @Transactional
    public MensagemResponse enviarManual(UUID entregaId, UUID motoristaId, String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            throw new RegraNegocioException("conteudo e obrigatorio.");
        }

        Entrega entrega = buscarEntregaDoMotoristaOuFalhar(entregaId, motoristaId);
        Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());
        if (!temEmail(bdo)) {
            throw new RecursoNaoEncontradoException("Esta entrega nao tem nenhum contato de passageiro disponivel.");
        }

        Optional<VinculoTelegram> vinculo = vinculoTelegramRepository.findByEntregaId(entregaId);
        Mensagem mensagemEmail = enviarPorEmail(entregaId, bdo, AutorMensagem.MOTORISTA, TipoMensagem.MANUAL, conteudo, null, vinculo.isEmpty());
        if (vinculo.isPresent()) {
            enviarPorTelegram(entregaId, vinculo.get().getChatId(), AutorMensagem.MOTORISTA, TipoMensagem.MANUAL, conteudo, null);
        }

        log.info("Mensagem manual enviada na conversa da entrega {} pelo motorista {}.", entregaId, motoristaId);
        // Resposta 201 representa o envio como um todo; quando os dois canais
        // sao usados, cada um gera seu proprio registro de Mensagem (FR-009 -
        // "os dois canais falham de forma independente"), mas o e-mail (canal
        // sempre presente quando ha contato) e o retornado ao chamador.
        return MensagemResponse.de(mensagemEmail);
    }

    /**
     * Implementacao real de {@code notificarAutomatica} (HU20, FR-005) - nunca
     * lanca: ausencia de contato disponivel e um no-op silencioso (mesmo
     * comportamento ja assumido pelos chamadores desde HU20).
     */
    @Transactional
    public void notificarAutomatica(UUID entregaId, TipoEventoNotificacao tipoEvento) {
        Optional<Entrega> entregaOpt = entregaService.buscarPorId(entregaId);
        if (entregaOpt.isEmpty()) {
            log.warn("notificarAutomatica chamado para uma entrega inexistente ({}) - ignorado.", entregaId);
            return;
        }
        Entrega entrega = entregaOpt.get();
        Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());
        if (!temEmail(bdo)) {
            log.debug("Notificacao automatica {} da entrega {} nao enviada - sem contato de passageiro disponivel.", tipoEvento, entregaId);
            return;
        }

        String conteudo = textoDoEvento(tipoEvento, bdo, entrega);
        Optional<VinculoTelegram> vinculo = vinculoTelegramRepository.findByEntregaId(entregaId);
        enviarPorEmail(entregaId, bdo, AutorMensagem.SISTEMA, TipoMensagem.AUTOMATICA, conteudo, tipoEvento, vinculo.isEmpty());
        if (vinculo.isPresent()) {
            enviarPorTelegram(entregaId, vinculo.get().getChatId(), AutorMensagem.SISTEMA, TipoMensagem.AUTOMATICA, conteudo, tipoEvento);
        }
        log.info("Notificacao automatica {} registrada para a entrega {}.", tipoEvento, entregaId);
    }

    /**
     * Chamado por {@code TelegramWebhookController} ao receber {@code /start
     * <entregaId>} (FR-003a), ja dentro do {@code TenantContext} do tenant
     * dono da entrega (resolvido pelo chamador antes de chegar aqui - o
     * webhook nao tem JWT).
     */
    @Transactional
    public void confirmarVinculoTelegram(UUID entregaId, long chatId) {
        if (vinculoTelegramRepository.findByEntregaId(entregaId).isEmpty()) {
            vinculoTelegramRepository.save(new VinculoTelegram(entregaId, chatId));
            log.info("VinculoTelegram criado: entrega {} <-> chat {}.", entregaId, chatId);
        }
        telegramClient.enviar(chatId, "Conversa conectada! A partir de agora voce recebe por aqui as atualizacoes desta entrega.");
    }

    /**
     * Chamado por {@code TelegramWebhookController} para uma mensagem livre
     * (nao {@code /start}), ja dentro do {@code TenantContext} do tenant
     * sendo tentado (o webhook nao tem JWT - o chamador itera os tenants
     * ate um deles ter um {@link VinculoTelegram} para este {@code chatId}).
     * {@code false} significa "este tenant nao e o dono deste chat" - o
     * chamador tenta o proximo, nunca um erro.
     */
    @Transactional
    public boolean processarAtualizacaoTelegram(long chatId, String texto) {
        List<VinculoTelegram> vinculos = vinculoTelegramRepository.findByChatId(chatId);
        if (vinculos.isEmpty()) {
            return false;
        }
        UUID entregaId = resolverEntregaPorChatId(vinculos);
        mensagemRepository.save(
                new Mensagem(entregaId, AutorMensagem.PASSAGEIRO, CanalMensagem.TELEGRAM, TipoMensagem.MANUAL, texto, StatusEnvioMensagem.RECEBIDA, null));
        log.info("Mensagem recebida via Telegram associada a entrega {} (chat {}).", entregaId, chatId);
        return true;
    }

    /** Chamado por {@code EmailInboundListener}, ja com o {@code entregaId} resolvido pelo endereco de destino (associacao exata, sem ambiguidade). */
    @Transactional
    public void registrarRecebidaEmail(UUID entregaId, String texto) {
        mensagemRepository.save(
                new Mensagem(entregaId, AutorMensagem.PASSAGEIRO, CanalMensagem.EMAIL, TipoMensagem.MANUAL, texto, StatusEnvioMensagem.RECEBIDA, null));
        log.info("Mensagem recebida via e-mail associada a entrega {}.", entregaId);
    }

    private Mensagem enviarPorEmail(
            UUID entregaId, Bdo bdo, AutorMensagem autor, TipoMensagem tipo, String conteudo, TipoEventoNotificacao tipoEvento,
            boolean incluirConviteTelegram) {
        boolean enviado = emailChannel.enviar(entregaId, bdo.getEmailPassageiro(), assuntoConversa(bdo), conteudo, incluirConviteTelegram);
        return mensagemRepository.save(new Mensagem(
                entregaId, autor, CanalMensagem.EMAIL, tipo, conteudo, enviado ? StatusEnvioMensagem.ENVIADA : StatusEnvioMensagem.FALHOU,
                tipoEvento));
    }

    private Mensagem enviarPorTelegram(
            UUID entregaId, long chatId, AutorMensagem autor, TipoMensagem tipo, String conteudo, TipoEventoNotificacao tipoEvento) {
        boolean enviado = telegramClient.enviar(chatId, conteudo);
        return mensagemRepository.save(new Mensagem(
                entregaId, autor, CanalMensagem.TELEGRAM, tipo, conteudo, enviado ? StatusEnvioMensagem.ENVIADA : StatusEnvioMensagem.FALHOU,
                tipoEvento));
    }

    /**
     * research.md item 3: um {@code chatId} pode ter mais de um {@code
     * VinculoTelegram} (entregas diferentes do mesmo passageiro). Com um so,
     * a resposta e exata; com mais de um, restringe as entregas "em aberto"
     * (nao encerradas com sucesso) - havendo exatamente uma, usa essa; caso
     * contrario (nenhuma ou mais de uma em aberto) e uma ambiguidade
     * genuina, sinalizada aqui por log estruturado (nao ha uma tela de
     * conferencia do administrador nesta HU) e resolvida de forma best-effort
     * pelo vinculo mais recente - preferivel a perder a mensagem.
     */
    private UUID resolverEntregaPorChatId(List<VinculoTelegram> vinculos) {
        if (vinculos.size() == 1) {
            return vinculos.get(0).getEntregaId();
        }

        List<VinculoTelegram> emAberto = vinculos.stream()
                .filter(v -> entregaService.buscarPorId(v.getEntregaId())
                        .map(e -> e.getStatus() != EntregaStatus.ENCERRADA_COM_SUCESSO)
                        .orElse(false))
                .toList();
        if (emAberto.size() == 1) {
            return emAberto.get(0).getEntregaId();
        }

        VinculoTelegram maisRecente = vinculos.stream().max(Comparator.comparing(VinculoTelegram::getCriadoEm)).orElseThrow();
        log.warn(
                "Ambiguidade ao associar mensagem do Telegram (chatId={}): {} vinculo(s) no total, {} em aberto - associada ao mais "
                        + "recente (entrega {}), sinalizada para conferencia do administrador do tenant.",
                vinculos.get(0).getChatId(), vinculos.size(), emAberto.size(), maisRecente.getEntregaId());
        return maisRecente.getEntregaId();
    }

    private String textoDoEvento(TipoEventoNotificacao tipoEvento, Bdo bdo, Entrega entrega) {
        String identificacao = "sua entrega (BDO " + bdo.getNumero() + ")";
        return switch (tipoEvento) {
            case VIAGEM_INICIADA -> "O motorista iniciou o trajeto de " + identificacao + ".";
            case A_CAMINHO -> "O motorista esta a caminho do endereco de " + identificacao + ".";
            case CHEGADA -> "O motorista chegou ao endereco de " + identificacao + ".";
            case COMPROVANTE_REGISTRADO -> "Entrega concluida! " + capitalizarPrimeiraLetra(identificacao) + " foi entregue com sucesso.";
            case OCORRENCIA_REGISTRADA ->
                    "Nao foi possivel concluir " + identificacao + " nesta tentativa; uma nova tentativa sera agendada.";
            case REAGENDAMENTO_REGISTRADO -> textoReagendamento(identificacao, entrega.janelaReagendamento());
        };
    }

    private String textoReagendamento(String identificacao, JanelaReagendamento janela) {
        if (janela == null) {
            return "A nova tentativa de " + identificacao + " foi reagendada.";
        }
        ZonedDateTime inicio = janela.inicio().atZone(fusoHorario);
        ZonedDateTime fim = janela.fim().atZone(fusoHorario);
        String faixa = inicio.toLocalDate().equals(fim.toLocalDate())
                ? DATA.format(inicio) + ", entre " + HORA.format(inicio) + " e " + HORA.format(fim)
                : "o periodo de " + DATA.format(inicio) + " " + HORA.format(inicio) + " a " + DATA.format(fim) + " " + HORA.format(fim);
        return "A nova tentativa de " + identificacao + " foi reagendada para " + faixa + ", como combinado.";
    }

    private String capitalizarPrimeiraLetra(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String assuntoConversa(Bdo bdo) {
        return PREFIXO_ASSUNTO_CONVERSA + bdo.getNumero();
    }

    private boolean temEmail(Bdo bdo) {
        return bdo.getEmailPassageiro() != null && !bdo.getEmailPassageiro().isBlank();
    }

    private Entrega buscarEntregaDoMotoristaOuFalhar(UUID entregaId, UUID motoristaId) {
        Entrega entrega =
                entregaService.buscarPorId(entregaId).orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new AcessoNegadoException("Esta entrega nao pertence ao motorista autenticado.");
        }
        return entrega;
    }

    private Bdo buscarBdoOuFalhar(UUID bdoId) {
        return bdoService.buscarPorId(bdoId).orElseThrow(() -> new IllegalStateException("Entrega referencia um BDO inexistente: " + bdoId));
    }
}
