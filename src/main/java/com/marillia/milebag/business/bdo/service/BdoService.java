package com.marillia.milebag.business.bdo.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.mapper.BdoMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoEncaminhadoPorCompanhia;
import com.marillia.milebag.business.bdo.model.BdoIncluidoPelaEmpresa;
import com.marillia.milebag.business.bdo.model.BdoOrigem;
import com.marillia.milebag.business.bdo.model.BdoRecebidoPorEmail;
import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Registro do BDO pelos tres caminhos de entrada: o telex que chega por e-mail (HU02), o encaminhamento da companhia
 * aerea parceira (HU37) e a inclusao pela empresa no painel (V49). A captura por foto do motorista (HU01) foi
 * desativada: os BDOs antigos dessa origem so aparecem no historico.
 */
@Service
public class BdoService {

    private static final Logger log = LoggerFactory.getLogger(BdoService.class);

    static final int TAMANHO_MAXIMO_TEXTO = LeitorTelexBdo.TAMANHO_MAXIMO_TEXTO;

    private final BdoRepository bdoRepository;
    private final BdoMapper bdoMapper;
    private final LeitorTelexBdo leitorTelexBdo;
    private final ApplicationEventPublisher eventos;

    public BdoService(BdoRepository bdoRepository, BdoMapper bdoMapper, LeitorTelexBdo leitorTelexBdo,
            ApplicationEventPublisher eventos) {
        this.bdoRepository = bdoRepository;
        this.bdoMapper = bdoMapper;
        this.leitorTelexBdo = leitorTelexBdo;
        this.eventos = eventos;
    }

    /** Ponto de leitura publico para outros dominios (rota, entrega, entrega.bagagemcarona) - nunca {@code BdoRepository} direto (CONVENTIONS.md 2.1). */
    @Transactional(readOnly = true)
    public Optional<Bdo> buscarPorId(UUID id) {
        return bdoRepository.findById(id);
    }

    /** Varios BDOs numa consulta so, por id (tela "Entregas" da empresa, V46). */
    @Transactional(readOnly = true)
    public Map<UUID, Bdo> buscarPorIds(Collection<UUID> ids) {
        return bdoRepository.findAllById(ids).stream().collect(Collectors.toMap(Bdo::getId, Function.identity()));
    }

    /** Volume de BDOs capturados no mes corrente do tenant ambiente (HU16, uso atual contra o limite do plano). */
    @Transactional(readOnly = true)
    public long contarNoMesCorrente() {
        Instant agora = Instant.now();
        Instant inicioDoMes = agora.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return bdoRepository.contarConfirmadosNaoCanceladosEntre(inicioDoMes, agora);
    }

    /** Tela "Entregas" da empresa (V47): BDOs confirmados ainda sem entrega, os mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<Bdo> listarSemEntrega() {
        return bdoRepository.findTop200SemEntrega();
    }

    /**
     * BDO por e-mail (RF21, HU02): a partir de um telex WorldTracer ja
     * reconhecido pelo {@code BdoTelexParser} no corpo de um e-mail - nasce
     * direto {@code CONFIRMADO}, guardando o texto como chegou. Pressupoe que o chamador ja
     * definiu o tenant da requisicao em {@link TenantContext} (ver
     * {@code BdoEmailListener}, que nao tem um JWT de onde resolve-lo).
     */
    @Transactional
    public void criarAutomatico(CamposExtraidosBdo camposExtraidos, String textoOriginal) {
        Bdo bdo = new Bdo();
        bdo.setOrigem(BdoOrigem.EMAIL_AUTOMATICO);
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        bdo.setDataCaptura(Instant.now());
        aplicarCampos(bdo, camposExtraidos);
        bdo.setTextoOriginal(limitar(semNul(textoOriginal), TAMANHO_MAXIMO_TEXTO));

        Bdo salvo = bdoRepository.save(bdo);
        log.info("BDO {} criado automaticamente por e-mail (tenant {})", salvo.getId(), TenantContext.get());
        // HU32: os motoristas sao avisados depois do commit (NotificadorBdoNovo).
        eventos.publishEvent(new BdoRecebidoPorEmail(salvo.getId(), salvo.getNumero()));
    }

    /**
     * Terceiro caminho de criacao do BDO (HU37, RF30): a companhia aerea encaminha uma bagagem encontrada para esta
     * empresa. Nasce {@code CONFIRMADO}, como o do e-mail, e entra na lista de disponiveis. Pressupoe o tenant da
     * empresa em {@link TenantContext} (ver {@code EncaminhamentoService}); grava na hora para o INSERT sair com ele.
     */
    @Transactional
    public UUID criarPorCompanhia(CamposExtraidosBdo campos) {
        Bdo bdo = new Bdo();
        bdo.setOrigem(BdoOrigem.COMPANHIA_AEREA);
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        bdo.setDataCaptura(Instant.now());
        aplicarCampos(bdo, campos);

        Bdo salvo = bdoRepository.saveAndFlush(bdo);
        log.info("BDO {} criado pela companhia {} (tenant {})", salvo.getId(), campos.companhiaAerea(), TenantContext.get());
        eventos.publishEvent(new BdoEncaminhadoPorCompanhia(salvo.getId(), salvo.getNumero(), TenantContext.get(), campos.companhiaAerea()));
        return salvo.getId();
    }

    /**
     * Cancelamento do encaminhamento (HU37): o BDO criado pela companhia sai, se ninguem o assumiu. A entrega e a
     * bagagem carona apontam para o BDO, entao a chave estrangeira tambem recusa numa corrida com o motorista.
     * Pressupoe o tenant da empresa em {@link TenantContext}.
     */
    @Transactional
    public void removerCriadoPorCompanhia(UUID id) {
        Bdo bdo = bdoRepository.findById(id)
                .filter(b -> b.getOrigem() == BdoOrigem.COMPANHIA_AEREA)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
        try {
            bdoRepository.delete(bdo);
            bdoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("A entrega desta bagagem ja tem motorista.");
        }
        log.info("BDO {} da companhia {} removido no cancelamento (tenant {})", id, bdo.getCompanhiaAerea(), TenantContext.get());
    }

    /**
     * Inclusao pela empresa (V49): o administrador cola o telex (lido por {@link #lerTexto}) ou digita os campos e
     * confere antes de salvar. Nasce {@code CONFIRMADO}, entra na lista de disponiveis e avisa os motoristas depois do
     * commit; o passageiro nao e avisado aqui (so quando a viagem inicia). Numero repetido e conferido antes pelo
     * painel ({@link #buscarPorNumero}) - nao bloqueia, porque a lista de cancelamento ja preve "BDO duplicado".
     */
    @Transactional
    public BdoResponse criarPelaEmpresa(CamposExtraidosBdo campos, String textoOriginal, UUID administradorId) {
        if (campos == null) {
            throw new RegraNegocioException("Informe os dados do BDO.");
        }
        CamposExtraidosBdo validos = validarParaInclusao(campos);
        Bdo bdo = new Bdo();
        bdo.setOrigem(BdoOrigem.EMPRESA);
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        bdo.setDataCaptura(Instant.now());
        bdo.setIncluidoPor(administradorId);
        aplicarCampos(bdo, validos);
        bdo.setTextoOriginal(textoOpcional(semNul(textoOriginal), TAMANHO_MAXIMO_TEXTO, "texto do BDO"));

        Bdo salvo = bdoRepository.saveAndFlush(bdo);
        log.info("BDO {} incluido pela empresa (tenant {})", salvo.getId(), TenantContext.get());
        eventos.publishEvent(new BdoIncluidoPelaEmpresa(salvo.getId(), salvo.getNumero()));
        return bdoMapper.toResponse(salvo);
    }

    /** Le o telex colado no painel e devolve os campos para a empresa conferir - nao grava nada. */
    public CamposExtraidosBdo lerTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RegraNegocioException("Cole o texto do BDO.");
        }
        if (texto.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new RegraNegocioException("O texto do BDO pode ter no maximo " + TAMANHO_MAXIMO_TEXTO + " caracteres.");
        }
        return leitorTelexBdo.ler(texto);
    }

    /** BDOs ja registrados com este numero (sem diferenciar maiusculas nem espacos): o painel avisa e pergunta. */
    @Transactional(readOnly = true)
    public List<BdoResponse> buscarPorNumero(String numero) {
        String normalizado = normalizarNumero(numero);
        if (normalizado == null) {
            return List.of();
        }
        return bdoRepository.buscarPorNumeroNormalizado(normalizado).stream().map(bdoMapper::toResponse).toList();
    }

    private void aplicarCampos(Bdo bdo, CamposExtraidosBdo campos) {
        bdo.setNumero(normalizarNumero(campos.numero()));
        bdo.setPassageiroNome(campos.passageiroNome());
        bdo.setEnderecoEntrega(enderecoCompleto(campos));
        bdo.setCompanhiaAerea(campos.companhiaAerea());
        bdo.setTelefonePassageiro(campos.telefonePassageiro());
        bdo.setEmailPassageiro(campos.emailPassageiro());
        bdo.setEtiquetaBagagem(campos.etiquetaBagagem());
        bdo.setTipoProcesso(campos.tipoProcesso());
        bdo.setCidade(campos.cidade());
        bdo.setUf(campos.uf());
        bdo.setCep(campos.cep());
        bdo.setInstrucoesEntrega(campos.instrucoesEntrega());
        bdo.setAeroportoRetirada(campos.aeroportoRetirada());
        bdo.setVooChegada(campos.vooChegada());
        bdo.setDataEntregaPrevista(campos.dataEntregaPrevista());
        bdo.setQuantidadeMalas(campos.quantidadeMalas());
        bdo.setCoresBagagem(campos.coresBagagem());
        bdo.setValorInformado(campos.valorInformado());
    }

    /**
     * Endereco usado na rota e mostrado ao motorista: logradouro, cidade - UF e CEP. A cidade so fica de fora quando o
     * logradouro ja termina nela (o telex as vezes escreve tudo na linha PA).
     */
    static String enderecoCompleto(CamposExtraidosBdo campos) {
        String logradouro = vazioParaNulo(campos.enderecoEntrega());
        List<String> partes = new ArrayList<>();
        if (logradouro != null) {
            partes.add(logradouro);
        }
        String cidade = vazioParaNulo(campos.cidade());
        String uf = vazioParaNulo(campos.uf());
        // So quando o logradouro TERMINA na cidade ("... APARECIDA DE GOIANIA"): "RUA GOIANIA 5" ainda precisa dela.
        boolean cidadeJaNoLogradouro = cidade != null && logradouro != null
                && logradouro.toUpperCase(Locale.ROOT).endsWith(cidade.toUpperCase(Locale.ROOT));
        if (cidade != null && !cidadeJaNoLogradouro) {
            partes.add(uf == null ? cidade : cidade + " - " + uf);
        } else if (uf != null && cidade == null) {
            partes.add(uf);
        }
        String cep = vazioParaNulo(campos.cep());
        if (cep != null) {
            partes.add(cep);
        }
        return partes.isEmpty() ? null : String.join(", ", partes);
    }

    /**
     * Campos minimos para viabilizar a entrega (numero, passageiro e endereco, os mesmos do e-mail) e o formato dos
     * demais, que a empresa pode ter digitado.
     */
    private static CamposExtraidosBdo validarParaInclusao(CamposExtraidosBdo c) {
        List<String> ausentes = new ArrayList<>();
        if (vazioParaNulo(c.numero()) == null) {
            ausentes.add("numero do BDO");
        }
        if (vazioParaNulo(c.passageiroNome()) == null) {
            ausentes.add("nome do passageiro");
        }
        if (vazioParaNulo(c.enderecoEntrega()) == null) {
            ausentes.add("endereco de entrega");
        }
        if (!ausentes.isEmpty()) {
            throw new RegraNegocioException("Campos obrigatorios ausentes: " + String.join(", ", ausentes) + ".");
        }
        String numero = normalizarNumero(c.numero());
        if (!numero.matches("[A-Z0-9]{4,30}")) {
            throw new RegraNegocioException("O numero do BDO deve ter de 4 a 30 letras e numeros, sem simbolos (ex.: GYNLA24713).");
        }
        String email = textoOpcional(c.emailPassageiro(), 200, "e-mail");
        if (email != null && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new RegraNegocioException("O e-mail do passageiro nao parece valido.");
        }
        String uf = maiusculo(vazioParaNulo(c.uf()));
        if (uf != null && !uf.matches("[A-Z]{2}")) {
            throw new RegraNegocioException("A UF deve ter duas letras (ex.: GO).");
        }
        String cep = vazioParaNulo(c.cep());
        if (cep != null) {
            String digitos = cep.replaceAll("\\D", "");
            if (digitos.length() != 8) {
                throw new RegraNegocioException("O CEP deve ter 8 numeros.");
            }
            cep = digitos.substring(0, 5) + "-" + digitos.substring(5);
        }
        String aeroporto = maiusculo(vazioParaNulo(c.aeroportoRetirada()));
        if (aeroporto != null && !aeroporto.matches("[A-Z]{3}")) {
            throw new RegraNegocioException("O aeroporto de retirada deve ser o codigo de tres letras (ex.: GYN).");
        }
        if (c.quantidadeMalas() != null && (c.quantidadeMalas() < 1 || c.quantidadeMalas() > 20)) {
            throw new RegraNegocioException("A quantidade de malas deve ficar entre 1 e 20.");
        }
        return new CamposExtraidosBdo(
                numero,
                textoOpcional(c.passageiroNome(), 200, "nome do passageiro"),
                textoOpcional(c.enderecoEntrega(), 300, "endereco de entrega"),
                textoOpcional(c.companhiaAerea(), 100, "companhia aerea"),
                textoOpcional(c.telefonePassageiro(), 30, "telefone"),
                email,
                textoOpcional(c.etiquetaBagagem(), 300, "etiquetas"),
                maiusculo(textoOpcional(c.tipoProcesso(), 10, "tipo do processo")),
                textoOpcional(c.cidade(), 100, "cidade"),
                uf,
                cep,
                textoOpcional(c.instrucoesEntrega(), 2000, "instrucoes de entrega"),
                aeroporto,
                maiusculo(textoOpcional(c.vooChegada(), 20, "voo")),
                c.dataEntregaPrevista(),
                c.quantidadeMalas(),
                maiusculo(textoOpcional(c.coresBagagem(), 200, "cores das malas")),
                textoOpcional(c.valorInformado(), 30, "valor informado"));
    }

    /** Texto opcional digitado: aparado, vazio vira nulo; acima do limite e recusado com o nome do campo. */
    private static String textoOpcional(String valor, int maximo, String campo) {
        String limpo = vazioParaNulo(valor);
        if (limpo != null && limpo.length() > maximo) {
            throw new RegraNegocioException("Campo " + campo + ": no maximo " + maximo + " caracteres.");
        }
        return limpo;
    }

    private static String normalizarNumero(String numero) {
        String limpo = vazioParaNulo(numero);
        return limpo == null ? null : limpo.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String maiusculo(String valor) {
        return valor == null ? null : valor.toUpperCase(Locale.ROOT);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String limitar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() > maximo ? valor.substring(0, maximo) : valor;
    }

    private static String semNul(String texto) {
        return texto == null ? null : texto.replace("\u0000", "");
    }
}
