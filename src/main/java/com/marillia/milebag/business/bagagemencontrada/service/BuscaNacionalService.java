package com.marillia.milebag.business.bagagemencontrada.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaNacional;
import com.marillia.milebag.business.bagagemencontrada.model.BuscaNacionalResponse;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaNacionalRepository;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Busca nacional por etiqueta (HU36, RD22): a unica consulta que atravessa as companhias. Le a tabela sem RLS mantida
 * pelo trigger (V44), que so tem os campos permitidos; da outra companhia nao sai nem o {@code id} do registro.
 */
@Service
public class BuscaNacionalService {

    private static final Logger log = LoggerFactory.getLogger(BuscaNacionalService.class);

    private final BagagemEncontradaNacionalRepository repository;
    private final TenantRepository tenantRepository;
    private final AeroportoService aeroportoService;

    public BuscaNacionalService(BagagemEncontradaNacionalRepository repository, TenantRepository tenantRepository, AeroportoService aeroportoService) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.aeroportoService = aeroportoService;
    }

    @Transactional(readOnly = true)
    public List<BuscaNacionalResponse> buscar(String etiqueta) {
        String numero = etiqueta == null ? "" : etiqueta.replaceAll("\s+", "").toUpperCase(Locale.ROOT);
        if (!BagagemEncontradaService.NUMERO_ETIQUETA.matcher(numero).matches()) {
            throw new RegraNegocioException("Informe o numero da etiqueta: 10 digitos ou 2 posicoes da companhia e 6 digitos (ex.: LA123456).");
        }
        List<BagagemEncontradaNacional> achadas = repository.findTop50ByNumeroEtiquetaOrderByRegistradaEmDesc(numero);
        UUID minhaCompanhia = TenantContext.get();
        Map<UUID, Tenant> companhias = tenantRepository.findAllById(achadas.stream().map(BagagemEncontradaNacional::getTenantId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
        log.info("Busca nacional pela etiqueta {} da companhia {}: {} registro(s).", numero, minhaCompanhia, achadas.size());
        return achadas.stream().map(achada -> {
            Tenant companhia = companhias.get(achada.getTenantId());
            boolean minha = achada.getTenantId().equals(minhaCompanhia);
            return new BuscaNacionalResponse(
                    new BuscaNacionalResponse.Companhia(companhia == null ? null : companhia.getNome(), companhia == null ? null : companhia.getCodigoIata()),
                    aeroportoService.buscar(achada.getAeroportoCodigo())
                            .map(a -> new BuscaNacionalResponse.Aeroporto(a.getCodigo(), a.getNome(), a.getCidade(), a.getUf()))
                            .orElse(new BuscaNacionalResponse.Aeroporto(achada.getAeroportoCodigo(), null, null, null)),
                    achada.getRegistradaEm(), achada.getLatitude(), achada.getLongitude(), minha, minha ? achada.getBagagemId() : null);
        }).toList();
    }
}
