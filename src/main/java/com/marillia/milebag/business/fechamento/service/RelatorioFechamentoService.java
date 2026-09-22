package com.marillia.milebag.business.fechamento.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.fechamento.model.Fechamento;
import com.marillia.milebag.business.fechamento.model.ItemFechamento;
import com.marillia.milebag.business.fechamento.model.StatusItemFechamento;

/**
 * Gera os dois relatorios em PDF do fechamento (FR-003/RN08, T015 desta
 * HU) - layout minimo e sem identidade visual (responsabilidade de HU13,
 * fora desta HU - ver contracts/fechamento-api.md, "Nota").
 */
@Service
public class RelatorioFechamentoService {

    private final FechamentoService fechamentoService;

    public RelatorioFechamentoService(FechamentoService fechamentoService) {
        this.fechamentoService = fechamentoService;
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioPagamentoEntregas(UUID fechamentoId) {
        return gerarPdf(fechamentoId, "Relatorio de Pagamento de Entregas", ItemFechamento::getValorPagamentoEntrega);
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioReembolsoPedagios(UUID fechamentoId) {
        return gerarPdf(fechamentoId, "Relatorio de Reembolso de Pedagios", ItemFechamento::getValorReembolsoPedagio);
    }

    private byte[] gerarPdf(UUID fechamentoId, String titulo, Function<ItemFechamento, BigDecimal> extrator) {
        Fechamento fechamento = fechamentoService.buscarOuFalhar(fechamentoId);
        List<ItemFechamento> itens = fechamentoService.itensDe(fechamentoId);

        Map<UUID, BigDecimal> totalPorMotorista = new LinkedHashMap<>();
        for (ItemFechamento item : itens) {
            if (item.getStatusItem() != StatusItemFechamento.CALCULADO) {
                continue;
            }
            totalPorMotorista.merge(item.getMotoristaId(), extrator.apply(item), BigDecimal::add);
        }
        long pendentes = itens.stream().filter(item -> item.getStatusItem() == StatusItemFechamento.PENDENTE).count();

        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);

            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                float y = 750;
                y = escreverLinha(conteudo, titulo, PDType1Font.HELVETICA_BOLD, 14, y);
                y = escreverLinha(
                        conteudo,
                        "Periodo: " + fechamento.getPeriodoInicio() + " a " + fechamento.getPeriodoFim(),
                        PDType1Font.HELVETICA,
                        11,
                        y - 6);
                y -= 20;

                if (totalPorMotorista.isEmpty()) {
                    y = escreverLinha(conteudo, "Nenhum valor calculado neste periodo.", PDType1Font.HELVETICA_OBLIQUE, 11, y);
                }
                for (Map.Entry<UUID, BigDecimal> entrada : totalPorMotorista.entrySet()) {
                    String linha = "Motorista " + entrada.getKey() + ": R$ " + entrada.getValue().setScale(2, RoundingMode.HALF_UP);
                    y = escreverLinha(conteudo, linha, PDType1Font.HELVETICA, 11, y);
                }

                if (pendentes > 0) {
                    y -= 10;
                    escreverLinha(
                            conteudo,
                            pendentes + " entrega(s) pendente(s) de configuracao, nao incluida(s) nos totais acima.",
                            PDType1Font.HELVETICA_OBLIQUE,
                            10,
                            y);
                }
            }

            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar o relatorio em PDF.", e);
        }
    }

    private float escreverLinha(PDPageContentStream conteudo, String texto, PDType1Font fonte, float tamanho, float y) throws IOException {
        conteudo.beginText();
        conteudo.setFont(fonte, tamanho);
        conteudo.newLineAtOffset(50, y);
        conteudo.showText(texto);
        conteudo.endText();
        return y - (tamanho + 6);
    }
}
