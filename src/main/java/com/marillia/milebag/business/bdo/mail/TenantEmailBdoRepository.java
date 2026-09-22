package com.marillia.milebag.business.bdo.mail;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sem Row Level Security (a tabela nao tem coluna de tenant discriminadora
 * alem da propria chave primaria) - {@link BdoEmailListener} precisa
 * enumerar os enderecos de todos os tenants, fora do contexto de uma
 * requisicao autenticada.
 */
public interface TenantEmailBdoRepository extends JpaRepository<TenantEmailBdo, UUID> {
}
