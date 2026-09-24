package com.marillia.milebag.business.fechamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService;
import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService.ParaRelatorio;
import com.marillia.milebag.business.fechamento.model.Fechamento;

/**
 * O cabecalho da identidade visual vem de um textarea (HU13): quebra de linha
 * CR/LF e caracteres fora do WinAnsi ja derrubaram a geracao do PDF inteiro
 * ao rodar contra a API real.
 */
@ExtendWith(MockitoExtension.class)
class RelatorioFechamentoServiceTest {

    @Mock
    private FechamentoService fechamentoService;
    @Mock
    private IdentidadeVisualTenantService identidadeVisualTenantService;

    private RelatorioFechamentoService service;
    private final UUID fechamentoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RelatorioFechamentoService(fechamentoService, identidadeVisualTenantService);
        when(fechamentoService.buscarOuFalhar(fechamentoId)).thenReturn(new Fechamento(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30)));
        when(fechamentoService.itensDe(fechamentoId)).thenReturn(List.of());
    }

    @Test
    void cabecalhoComQuebraDeLinhaCrLfGeraPdfComCadaLinha() throws IOException {
        when(identidadeVisualTenantService.carregarParaRelatorio())
                .thenReturn(new ParaRelatorio("Transportes Alfa Ltda\r\nCNPJ 00.000.000/0001-00", Optional.empty()));

        String texto = textoDe(service.gerarRelatorioPagamentoEntregas(fechamentoId));

        assertThat(texto).contains("Transportes Alfa Ltda").contains("CNPJ 00.000.000/0001-00");
    }

    @Test
    void cabecalhoComCaracterForaDoWinAnsiNaoDerrubaOPdf() throws IOException {
        when(identidadeVisualTenantService.carregarParaRelatorio())
                .thenReturn(new ParaRelatorio("Alfa\tLtda ☃ Logistica", Optional.empty()));

        String texto = textoDe(service.gerarRelatorioReembolsoPedagios(fechamentoId));

        assertThat(texto).contains("Alfa Ltda ? Logistica");
    }

    private String textoDe(byte[] pdf) throws IOException {
        try (PDDocument documento = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
