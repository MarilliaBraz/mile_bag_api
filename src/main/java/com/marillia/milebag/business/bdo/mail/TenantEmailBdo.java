package com.marillia.milebag.business.bdo.mail;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Endereco de recebimento automatico de BDO de um tenant (T005b), ex.:
 * {@code bdo-{tenantId}@recebimento.<dominio>} - provisionado no cadastro do
 * tenant (HU14, ainda nao implementada).
 *
 * <p>Nao extende {@link com.marillia.milebag.core.base.BaseEntity}: a chave
 * primaria e o proprio {@code tenantId} (uma linha por tenant) e a tabela e
 * de configuracao de plataforma, sem Row Level Security - ver migracao
 * V3.</p>
 */
@Entity
@Table(name = "tenant_email_bdo")
@Getter
@Setter
@NoArgsConstructor
public class TenantEmailBdo {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, unique = true, length = 255)
    private String endereco;
}
