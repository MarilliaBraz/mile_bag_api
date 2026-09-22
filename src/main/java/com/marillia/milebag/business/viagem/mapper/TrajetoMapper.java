package com.marillia.milebag.business.viagem.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.viagem.model.CalculoRota;
import com.marillia.milebag.business.viagem.model.CalculoRotaResponse;
import com.marillia.milebag.business.viagem.model.CheckpointGps;
import com.marillia.milebag.business.viagem.model.CheckpointResponse;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.model.TrajetoDetalhadoResponse;
import com.marillia.milebag.business.viagem.model.TrajetoIniciadoResponse;
import com.marillia.milebag.business.viagem.model.TrechoPedagio;

@Component
public class TrajetoMapper {

    private final ObjectMapper objectMapper;

    public TrajetoMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CalculoRotaResponse toResponse(CalculoRota calculoRota) {
        return new CalculoRotaResponse(
                calculoRota.getId(),
                calculoRota.getKmIda(),
                calculoRota.getKmVolta(),
                lerPracasPedagio(calculoRota.getPracasPedagio()),
                calculoRota.getTempoEstimado(),
                calculoRota.getOrigemSelecao(),
                calculoRota.getDataHora(),
                calculoRota.isEhRotaInicial());
    }

    public CheckpointResponse toResponse(CheckpointGps checkpoint) {
        return new CheckpointResponse(
                checkpoint.getId(),
                checkpoint.getTipo(),
                checkpoint.getLat(),
                checkpoint.getLng(),
                checkpoint.getDataHora(),
                checkpoint.isAusente());
    }

    public TrajetoIniciadoResponse toIniciadoResponse(Trajeto trajeto, CalculoRota calculoInicial, CheckpointGps checkpointPartida) {
        return new TrajetoIniciadoResponse(
                trajeto.getId(), trajeto.getStatus(), trajeto.getDataInicio(), toResponse(calculoInicial), toResponse(checkpointPartida));
    }

    public TrajetoDetalhadoResponse toDetalhadoResponse(
            Trajeto trajeto, List<CalculoRota> calculosRota, List<CheckpointGps> checkpoints) {
        return new TrajetoDetalhadoResponse(
                trajeto.getId(),
                trajeto.getStatus(),
                trajeto.getDataInicio(),
                calculosRota.stream().map(this::toResponse).toList(),
                checkpoints.stream().map(this::toResponse).toList(),
                trajeto.getKmIdaFinal(),
                trajeto.getKmVoltaFinal(),
                lerPracasPedagio(trajeto.getPracasPedagioFinal()),
                trajeto.getTempoFinal(),
                trajeto.getJustificativaDivergencia());
    }

    private List<TrechoPedagio> lerPracasPedagio(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TrechoPedagio>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler as pracas de pedagio persistidas.", e);
        }
    }
}
