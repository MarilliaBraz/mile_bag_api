package com.marillia.milebag.business.tenant.dto;

/** Formato de resposta do GET /api/v1/convites/{token} (contracts/convite-api.md). */
public record ConviteConsultaResponse(String tenantNome, String emailDestinatario, String status) {
}
