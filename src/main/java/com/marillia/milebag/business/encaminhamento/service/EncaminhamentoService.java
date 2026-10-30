package com.marillia.milebag.business.encaminhamento.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.bagagemencontrada.service.BagagemEncontradaService;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.encaminhamento.model.BagagemEncaminhada;
import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;
import com.marillia.milebag.business.encaminhamento.model.EncaminharBagagemRequest;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.repository.EncaminhamentoEntregaRepository;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Encaminhamento da bagagem encontrada para uma empresa de entrega parceira (HU37, RF30, RD23).
 *
 * <p>Uma transacao, dois tenants: a bagagem e o encaminhamento sao lidos e gravados com o tenant da companhia; o BDO e
 * criado (ou apagado) com o tenant da empresa, trocado so durante a chamada ao {@link BdoService}, que grava na hora
 * ({@code saveAndFlush}) - o TenantAspect aplica o tenant a cada consulta, entao o INSERT sai com o da empresa e passa
 * pela RLS dela. Nada da companhia fica pendente de gravacao nesse intervalo.</p>
 */
@Service
public class EncaminhamentoService {

    private static final Logger log = LoggerFactory.getLogger(EncaminhamentoService.class);

    /** O numero do processo do WorldTracer (ex.: GRUZZ12345) - so letras e digitos. */
    private static final Pattern NUMERO_PROCESSO = Pattern.compile("[A-Z0-9]{4,30}");
    private static final Pattern TELEFONE = Pattern.compile("\\+?[0-9 ()-]{8,30}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final EncaminhamentoEntregaRepository repository;
    private final BagagemEncontradaRepository bagagemRepository;
    private final BagagemEncontradaService bagagemService;
    private final ParceriaEntregaService parceriaService;
    private final TenantRepository tenantRepository;
    private final BdoService bdoService;
    private final ApplicationEventPublisher eventos;

    public EncaminhamentoService(
            EncaminhamentoEntregaRepository repository, BagagemEncontradaRepository bagagemRepository, BagagemEncontradaService bagagemService,
            ParceriaEntregaService parceriaService, TenantRepository tenantRepository, BdoService bdoService, ApplicationEventPublisher eventos) {
        this.repository = repository;
        this.bagagemRepository = bagagemRepository;
        this.bagagemService = bagagemService;
        this.parceriaService = parceriaService;
        this.tenantRepository = tenantRepository;
        this.bdoService = bdoService;
        this.eventos = eventos;
    }

    @Transactional
    public BagagemEncontradaResponse encaminhar(UUID bagagemId, EncaminharBagagemRequest request, UUID funcionarioId) {
        UUID companhiaId = TenantContext.get();
        BagagemEncontrada bagagem = bagagemRepository.findById(bagagemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bagagem nao encontrada."));
        if (bagagem.rascunho()) {
            throw new ConflitoException("Registre a bagagem antes de encaminhar para entrega.");
        }
        if (repository.findByBagagemIdAndCompanhiaIdAndEtapaNot(bagagemId, companhiaId, EtapaEntrega.CANCELADO).isPresent()) {
            throw new ConflitoException("Esta bagagem ja foi encaminhada para entrega.");
        }

        String processo = obrigatorio(request.numeroProcesso(), 30, "o numero do processo").replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!NUMERO_PROCESSO.matcher(processo).matches()) {
            throw new RegraNegocioException("Numero do processo invalido: use so letras e digitos, como no WorldTracer (ex.: GRUZZ12345).");
        }
        String passageiro = obrigatorio(request.passageiroNome(), 200, "o nome do passageiro");
        String endereco = obrigatorio(request.endereco(), 300, "o endereco de entrega");
        String telefone = opcional(request.telefone(), TELEFONE, "Telefone invalido.");
        String email = opcional(request.email(), EMAIL, "E-mail invalido.");
        if (email != null && email.length() > 200) {
            throw new RegraNegocioException("E-mail invalido.");
        }
        if (!parceriaService.saoParceiras(companhiaId, request.empresaId())) {
            throw new RegraNegocioException("Escolha uma empresa de entrega parceira da sua companhia.");
        }

        Tenant companhia = tenantRepository.findById(companhiaId).orElseThrow();
        Tenant empresa = tenantRepository.findById(request.empresaId()).orElseThrow();
        String nomeCompanhia = companhia.getNome() + " (" + companhia.getCodigoIata() + ")";
        String identificacao = bagagem.semEtiqueta() ? bagagem.getCodigoSid() : bagagem.getNumeroEtiqueta();

        UUID bdoId = naEmpresa(empresa.getId(), () -> bdoService.criarPorCompanhia(
                new CamposExtraidosBdo(processo, passageiro, endereco, nomeCompanhia, telefone, email, identificacao)));
        EncaminhamentoEntrega encaminhamento;
        try {
            encaminhamento = repository.saveAndFlush(new EncaminhamentoEntrega(
                    bagagemId, companhiaId, empresa.getId(), bdoId, processo, passageiro, endereco, funcionarioId, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            // Dois encaminhamentos ao mesmo tempo: o indice unico do ativo (V45) segura o segundo, e o BDO dele volta junto.
            throw new ConflitoException("Esta bagagem ja foi encaminhada para entrega.");
        }
        log.info("Bagagem {} da companhia {} encaminhada para {} (BDO {}, processo {}).",
                bagagemId, companhia.getCodigoIata(), empresa.getIdentificadorEmpresa(), bdoId, processo);

        if (email != null) {
            eventos.publishEvent(new BagagemEncaminhada(encaminhamento.getId(), email, passageiro, nomeCompanhia, empresa.getNome(), identificacao));
        }
        return bagagemService.buscar(bagagemId);
    }

    /** So enquanto nenhum motorista assumiu (RD23); o BDO sai da empresa e a bagagem pode ser encaminhada de novo. */
    @Transactional
    public void cancelar(UUID bagagemId, UUID funcionarioId) {
        UUID companhiaId = TenantContext.get();
        bagagemRepository.findById(bagagemId).orElseThrow(() -> new RecursoNaoEncontradoException("Bagagem nao encontrada."));
        EncaminhamentoEntrega encaminhamento = repository.findByBagagemIdAndCompanhiaIdAndEtapaNot(bagagemId, companhiaId, EtapaEntrega.CANCELADO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esta bagagem nao tem encaminhamento ativo."));
        if (encaminhamento.getEtapa() != EtapaEntrega.AGUARDANDO_MOTORISTA) {
            throw new ConflitoException("A entrega desta bagagem ja tem motorista: combine o cancelamento com a empresa de entrega.");
        }
        UUID bdoId = encaminhamento.getBdoId();
        if (bdoId != null) {
            naEmpresa(encaminhamento.getEmpresaId(), () -> {
                bdoService.removerCriadoPorCompanhia(bdoId);
                return null;
            });
        }
        encaminhamento.cancelar(funcionarioId, Instant.now());
        repository.save(encaminhamento);
        log.info("Encaminhamento da bagagem {} cancelado (BDO {}).", bagagemId, bdoId);
    }

    /** Roda a acao com o tenant da empresa e volta ao da companhia, mesmo se a acao falhar. */
    private static <T> T naEmpresa(UUID empresaId, Supplier<T> acao) {
        UUID companhiaId = TenantContext.get();
        TenantContext.set(empresaId);
        try {
            return acao.get();
        } finally {
            TenantContext.set(companhiaId);
        }
    }

    private static String obrigatorio(String valor, int maximo, String campo) {
        String limpo = valor == null ? "" : valor.strip();
        if (limpo.isEmpty()) {
            throw new RegraNegocioException("Informe " + campo + ".");
        }
        if (limpo.length() > maximo) {
            throw new RegraNegocioException("Use no maximo " + maximo + " caracteres para " + campo + ".");
        }
        return limpo;
    }

    private static String opcional(String valor, Pattern formato, String mensagem) {
        String limpo = valor == null ? "" : valor.strip();
        if (limpo.isEmpty()) {
            return null;
        }
        if (!formato.matcher(limpo).matches()) {
            throw new RegraNegocioException(mensagem);
        }
        return limpo;
    }
}
