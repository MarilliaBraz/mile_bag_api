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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService;
import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService.ParaRelatorio;
import com.marillia.milebag.business.fechamento.model.Fechamento;
import com.marillia.milebag.business.fechamento.model.ItemFechamento;
import com.marillia.milebag.business.fechamento.model.StatusItemFechamento;

/**
 * Gera os dois relatorios em PDF do fechamento (FR-003/RN08, T015 de HU12)
 * aplicando a identidade visual do tenant por cima do layout base (HU13,
 * FR-002) - logotipo e cabecalho sao lidos em tempo de geracao (nunca
 * cacheados no Fechamento), entao uma alteracao posterior da identidade
 * nao reprocessa relatorios ja emitidos (FR-005) e so afeta os proximos.
 *
 * <p>{@code plan.md} desta HU registra JasperReports como preferivel "para
 * templates visuais parametrizaveis por tenant". Decisao desta
 * implementacao: manter PDFBox (ja usado por HU12) em vez de introduzir uma
 * segunda biblioteca de relatorio so para desenhar uma imagem e uma linha
 * de texto no topo da pagina - PDFBox ja faz isso nativamente
 * ({@link PDImageXObject}), e adotar JasperReports exigiria templates
 * .jrxml e um motor de compilacao que nada aqui precisa. Reavaliar se uma
 * HU futura pedir templates visuais de fato parametrizaveis (cores, layout
 * arbitrario por tenant) alem de logotipo/cabecalho.</p>
 */
@Service
public class RelatorioFechamentoService {

    private final FechamentoService fechamentoService;
    private final IdentidadeVisualTenantService identidadeVisualTenantService;

    public RelatorioFechamentoService(
            FechamentoService fechamentoService, IdentidadeVisualTenantService identidadeVisualTenantService) {
        this.fechamentoService = fechamentoService;
        this.identidadeVisualTenantService = identidadeVisualTenantService;
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
        long tentativas = itens.stream()
                .filter(item -> item.getStatusItem() == StatusItemFechamento.CALCULADO && item.isTentativaSemSucesso())
                .count();
        ParaRelatorio identidade = identidadeVisualTenantService.carregarParaRelatorio();

        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);

            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                float y = 750;
                y = aplicarIdentidadeVisual(documento, conteudo, identidade, y);
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

                if (tentativas > 0) {
                    y -= 10;
                    y = escreverLinha(
                            conteudo,
                            "Inclui " + tentativas + " viagem(ns) de tentativa sem sucesso (ocorrencia), paga(s) a quem dirigiu.",
                            PDType1Font.HELVETICA_OBLIQUE,
                            10,
                            y);
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

    /**
     * Desenha logotipo (se presente) e cabecalho (se presente) no topo da
     * pagina, antes do titulo do relatorio; sem nenhum dos dois, a pagina
     * segue exatamente como antes desta HU (aparencia padrao neutra, FR-004).
     */
    private float aplicarIdentidadeVisual(PDDocument documento, PDPageContentStream conteudo, ParaRelatorio identidade, float y) throws IOException {
        if (identidade.logotipo().isPresent()) {
            PDImageXObject imagem = PDImageXObject.createFromByteArray(documento, identidade.logotipo().get(), "logotipo");
            float alturaMaxima = 50;
            float escala = Math.min(1f, alturaMaxima / imagem.getHeight());
            float largura = imagem.getWidth() * escala;
            float altura = imagem.getHeight() * escala;
            conteudo.drawImage(imagem, 50, y - altura, largura, altura);
            y -= altura + 10;
        }
        if (identidade.cabecalho() != null && !identidade.cabecalho().isBlank()) {
            // O cabecalho vem de um textarea: showText nao aceita quebra de linha (CR/LF), entao cada linha e desenhada a parte.
            for (String linha : identidade.cabecalho().strip().split("\\R", -1)) {
                y = escreverLinha(conteudo, linha, PDType1Font.HELVETICA_BOLD, 12, y);
            }
            y -= 10;
        }
        return y;
    }

    private float escreverLinha(PDPageContentStream conteudo, String texto, PDType1Font fonte, float tamanho, float y) throws IOException {
        conteudo.beginText();
        conteudo.setFont(fonte, tamanho);
        conteudo.newLineAtOffset(50, y);
        conteudo.showText(somenteCaracteresDaFonte(fonte, texto));
        conteudo.endText();
        return y - (tamanho + 6);
    }

    /** As fontes padrao do PDF so cobrem WinAnsi; texto livre do tenant (emoji, tab) derrubaria a geracao inteira. */
    private String somenteCaracteresDaFonte(PDType1Font fonte, String texto) {
        StringBuilder seguro = new StringBuilder(texto.length());
        texto.codePoints().forEach(codigo -> {
            String caractere = new String(Character.toChars(codigo));
            try {
                fonte.encode(caractere);
                seguro.append(caractere);
            } catch (IOException | IllegalArgumentException e) {
                seguro.append(codigo == '\t' ? ' ' : '?');
            }
        });
        return seguro.toString();
    }
}
