package com.marillia.milebag.business.dadosbdo.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.DadosPassageiroBdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.dadosbdo.model.AlteracaoDadosBdo;
import com.marillia.milebag.business.dadosbdo.model.DadosBdoAlterados;
import com.marillia.milebag.business.dadosbdo.model.ValoresPassageiro;
import com.marillia.milebag.business.dadosbdo.repository.AlteracaoDadosBdoRepository;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.rota.service.RotaService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.util.TextoLivre;

/**
 * Correcao dos dados do passageiro de um BDO pela empresa (V59): nome, endereco, telefone e e-mail - ex.: o passageiro
 * ligou mudando o endereco. Vale ate a entrega ser concluida (inclusive com a viagem em andamento), sempre com motivo;
 * o antes e o depois ficam no historico da mala e o motorista que esta com a entrega e avisado.
 */
@Service
public class AlteracaoDadosBdoService {

    private static final Logger log = LoggerFactory.getLogger(AlteracaoDadosBdoService.class);
    private static final int TAMANHO_MAXIMO_MOTIVO = 500;

    private final AlteracaoDadosBdoRepository repository;
    private final BdoService bdoService;
    private final EntregaService entregaService;
    private final BagagemCaronaService bagagemCaronaService;
    private final RotaService rotaService;
    private final ApplicationEventPublisher eventos;

    public AlteracaoDadosBdoService(AlteracaoDadosBdoRepository repository, BdoService bdoService, EntregaService entregaService,
            BagagemCaronaService bagagemCaronaService, RotaService rotaService, ApplicationEventPublisher eventos) {
        this.repository = repository;
        this.bdoService = bdoService;
        this.entregaService = entregaService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.rotaService = rotaService;
        this.eventos = eventos;
    }

    /** Os dados atuais do passageiro, para o formulario de correcao (o logradouro separado de cidade, UF e CEP). */
    @Transactional(readOnly = true)
    public DadosPassageiroBdo dados(UUID bdoId) {
        Bdo bdo = bdoService.buscarPorId(bdoId)
                .filter(encontrado -> encontrado.getStatusConferencia() == StatusConferencia.CONFIRMADO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
        return BdoService.dadosDoPassageiro(bdo);
    }

    @Transactional
    public AlteracaoDadosBdo alterar(UUID bdoId, DadosPassageiroBdo dados, String motivo, UUID administradorId) {
        String motivoLimpo = TextoLivre.opcional(motivo, TAMANHO_MAXIMO_MOTIVO, "motivo da alteracao");
        if (motivoLimpo == null) {
            throw new RegraNegocioException("Informe o motivo da alteracao.");
        }
        DadosPassageiroBdo validos = BdoService.validarDadosDoPassageiro(dados);
        // Travados o BDO e depois a entrega (e a principal, se for carona) - a mesma ordem do autoatribuir: um
        // comprovante, cancelamento ou autoatribuicao ao mesmo tempo espera, e as regras abaixo veem a situacao final.
        Bdo bdo = bdoService.travar(bdoId)
                .filter(encontrado -> encontrado.getStatusConferencia() == StatusConferencia.CONFIRMADO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
        Optional<Entrega> entrega = entregaService.travarPorBdo(bdoId);
        Optional<Entrega> principal = bagagemCaronaService.entregaPrincipalIdDe(bdoId).map(entregaService::travarParaCorrigir);
        exigirEntregaEmAberto(entrega, principal);

        // Comparado com o que o formulario mostrou: um BDO antigo, com o endereco num campo so, nao "muda de endereco"
        // so porque o telefone foi corrigido.
        boolean enderecoMudou = !mesmoEndereco(BdoService.dadosDoPassageiro(bdo), validos);
        ValoresPassageiro anterior = valoresDe(bdo);
        ValoresPassageiro novo = new ValoresPassageiro(validos.passageiroNome(),
                enderecoMudou ? BdoService.enderecoCompleto(validos) : anterior.endereco(),
                validos.telefonePassageiro(), validos.emailPassageiro());
        if (!enderecoMudou && anterior.mesmoQue(novo)) {
            throw new RegraNegocioException("Nenhum dado foi alterado.");
        }
        if (enderecoMudou) {
            exigirSemCarona(entrega, principal);
        }

        bdoService.alterarDadosDoPassageiro(bdo, validos, enderecoMudou);
        AlteracaoDadosBdo alteracao = repository.save(
                new AlteracaoDadosBdo(bdoId, anterior, novo, motivoLimpo, administradorId, Instant.now()));
        if (enderecoMudou) {
            entrega.ifPresent(e -> rotaService.aoCorrigirEnderecoDeEntrega(
                    e.getId(), novo.endereco(), e.getStatus() == EntregaStatus.EM_ANDAMENTO));
        }
        UUID motorista = entrega.map(Entrega::getMotoristaResponsavel)
                .or(() -> principal.map(Entrega::getMotoristaResponsavel))
                .orElse(null);
        if (motorista != null) {
            eventos.publishEvent(new DadosBdoAlterados(bdoId, bdo.getNumero(), motorista, enderecoMudou));
        }
        log.info("Dados do passageiro do BDO {} corrigidos por {} (endereco mudou: {}).", bdoId, administradorId, enderecoMudou);
        return alteracao;
    }

    @Transactional(readOnly = true)
    public List<AlteracaoDadosBdo> listarPorBdo(UUID bdoId) {
        return repository.findByBdoIdOrderByDataHoraAsc(bdoId);
    }

    /** Concluida ou cancelada, nao muda mais: a cobranca da viagem ja feita sai da cidade do BDO. */
    private static void exigirEntregaEmAberto(Optional<Entrega> entrega, Optional<Entrega> principal) {
        Optional<EntregaStatus> status = entrega.or(() -> principal).map(Entrega::getStatus);
        if (status.filter(EntregaStatus.ENCERRADA_COM_SUCESSO::equals).isPresent()) {
            throw new ConflitoException("A entrega deste BDO ja foi concluida; os dados nao podem mais ser alterados.");
        }
        if (entrega.map(Entrega::getStatus).filter(EntregaStatus.CANCELADA::equals).isPresent()) {
            throw new ConflitoException("Este BDO foi cancelado; os dados nao podem mais ser alterados.");
        }
    }

    /** A carona so e ligada a uma entrega para o mesmo endereco, com o km complementar calculado na hora. */
    private void exigirSemCarona(Optional<Entrega> entrega, Optional<Entrega> principal) {
        if (principal.isPresent()) {
            throw new ConflitoException("Esta bagagem viaja como carona de outra entrega; desfaca a carona antes de mudar o endereco.");
        }
        if (entrega.isPresent() && !bagagemCaronaService.listarPorEntrega(entrega.get().getId()).isEmpty()) {
            throw new ConflitoException("Esta entrega leva bagagem carona; desfaca a carona antes de mudar o endereco.");
        }
    }

    private static ValoresPassageiro valoresDe(Bdo bdo) {
        return new ValoresPassageiro(bdo.getPassageiroNome(), bdo.getEnderecoEntrega(), bdo.getTelefonePassageiro(),
                bdo.getEmailPassageiro());
    }

    /** Logradouro, cidade, UF e CEP iguais, sem diferenciar maiusculas, espacos nas pontas nem a mascara do CEP. */
    private static boolean mesmoEndereco(DadosPassageiroBdo atuais, DadosPassageiroBdo validos) {
        return mesmoTexto(atuais.enderecoEntrega(), validos.enderecoEntrega()) && mesmoTexto(atuais.cidade(), validos.cidade())
                && mesmoTexto(atuais.uf(), validos.uf()) && Objects.equals(digitos(atuais.cep()), digitos(validos.cep()));
    }

    private static boolean mesmoTexto(String a, String b) {
        String x = a == null || a.isBlank() ? null : a.strip();
        String y = b == null || b.isBlank() ? null : b.strip();
        return x == null ? y == null : x.equalsIgnoreCase(y);
    }

    private static String digitos(String cep) {
        String apenas = cep == null ? "" : cep.replaceAll("\\D", "");
        return apenas.isEmpty() ? null : apenas;
    }
}
