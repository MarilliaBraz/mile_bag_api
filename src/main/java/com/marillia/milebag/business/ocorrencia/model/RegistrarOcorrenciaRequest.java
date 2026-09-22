package com.marillia.milebag.business.ocorrencia.model;

import java.util.UUID;

public record RegistrarOcorrenciaRequest(UUID motivoOcorrenciaId, String descricaoAdicional, String idLocal) {
}
