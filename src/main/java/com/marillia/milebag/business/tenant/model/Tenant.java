package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Empresa assinante do sistema (HU14) - diferente de toda entidade de
 * dominio ja existente, NAO estende {@code BaseEntity}: {@code id} aqui E o
 * proprio identificador de tenant usado como discriminador de RLS em todas
 * as outras tabelas, nao uma referencia a ele - nao faz sentido esta linha
 * ter, ela mesma, um {@code tenantId} apontando para si. Ver V21 (migracao)
 * para a decisao de nao ter Row Level Security nesta tabela.
 *
 * <p>Nao guarda {@code planoId} (removido na migracao V24, HU16): o plano
 * vigente de um tenant vive exclusivamente em {@link HistoricoPlanoTenant},
 * unica fonte de verdade (tasks.md de HU16, T016) - permite trocar de
 * plano (FR-004) sem um campo redundante aqui.</p>
 */
@Entity
@Table(name = "tenant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "identificador_empresa", updatable = false, length = 32)
    private String identificadorEmpresa;

    @Column(length = 200)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatusTenant status;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private Instant criadoEm;

    /** RD19 (HU33): empresa de entrega ou companhia aerea. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private TipoTenant tipo;

    /** So nas companhias aereas: codigo IATA de duas posicoes, unico entre elas (V40). */
    @Column(name = "codigo_iata", length = 2, updatable = false)
    private String codigoIata;

    public Tenant(String identificadorEmpresa, String nome) {
        this.identificadorEmpresa = identificadorEmpresa;
        this.nome = nome;
        this.status = StatusTenant.ATIVO;
        this.tipo = TipoTenant.EMPRESA_ENTREGA;
    }

    /** Companhia aerea (HU33). */
    public static Tenant companhiaAerea(String identificador, String nome, String codigoIata) {
        Tenant companhia = new Tenant(identificador, nome);
        companhia.tipo = TipoTenant.COMPANHIA_AEREA;
        companhia.codigoIata = codigoIata;
        return companhia;
    }

    public boolean ehCompanhiaAerea() {
        return tipo == TipoTenant.COMPANHIA_AEREA;
    }
}
