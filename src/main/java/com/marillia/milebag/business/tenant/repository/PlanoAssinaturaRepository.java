package com.marillia.milebag.business.tenant.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.tenant.model.PlanoAssinatura;

public interface PlanoAssinaturaRepository extends JpaRepository<PlanoAssinatura, UUID> {
}
