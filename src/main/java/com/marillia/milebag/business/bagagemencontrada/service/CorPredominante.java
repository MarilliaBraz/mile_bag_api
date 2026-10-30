package com.marillia.milebag.business.bagagemencontrada.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;

/**
 * Cor sugerida para a bagagem sem etiqueta (HU35, RF28), tirada da propria foto sem servico externo (RNF08): a foto e
 * reduzida, cada pixel da regiao central (onde a bagagem costuma estar; o chao e o fundo ficam nas bordas) vira uma cor
 * nomeada pelo matiz, saturacao e brilho, e vence a mais frequente - desde que cubra ao menos um quarto da regiao.
 */
public final class CorPredominante {

    private static final int LADO_REDUZIDO = 96;
    /** Fracao da largura e da altura descartada em cada borda. */
    private static final double MARGEM = 0.2;
    private static final double PARTICIPACAO_MINIMA = 0.25;

    private CorPredominante() {
    }

    public static Optional<CorBagagem> sugerir(BufferedImage foto) {
        BufferedImage reduzida = reduzir(foto);
        int largura = reduzida.getWidth();
        int altura = reduzida.getHeight();
        Map<CorBagagem, Integer> contagem = new EnumMap<>(CorBagagem.class);
        int total = 0;
        float[] hsb = new float[3];
        for (int x = (int) (largura * MARGEM); x < largura - (int) (largura * MARGEM); x++) {
            for (int y = (int) (altura * MARGEM); y < altura - (int) (altura * MARGEM); y++) {
                int rgb = reduzida.getRGB(x, y);
                Color.RGBtoHSB((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, hsb);
                contagem.merge(classificar(hsb[0] * 360, hsb[1], hsb[2]), 1, Integer::sum);
                total++;
            }
        }
        if (total == 0) {
            return Optional.empty();
        }
        int minimo = (int) Math.ceil(total * PARTICIPACAO_MINIMA);
        return contagem.entrySet().stream()
                .filter(e -> e.getValue() >= minimo)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    /** Matiz em graus (0-360), saturacao e brilho de 0 a 1. */
    static CorBagagem classificar(float matiz, float saturacao, float brilho) {
        if (brilho < 0.2f) {
            return CorBagagem.PRETA;
        }
        if (saturacao < 0.15f) {
            return brilho > 0.85f ? CorBagagem.BRANCA : CorBagagem.CINZA;
        }
        // Tons de laranja claros e pouco saturados sao bege; escuros sao marrom.
        if (matiz >= 15 && matiz < 50 && saturacao < 0.4f && brilho >= 0.6f) {
            return CorBagagem.BEGE;
        }
        if (matiz >= 10 && matiz < 45 && brilho < 0.6f) {
            return CorBagagem.MARROM;
        }
        if (matiz < 15 || matiz >= 345) {
            return CorBagagem.VERMELHA;
        }
        if (matiz < 45) {
            return CorBagagem.LARANJA;
        }
        if (matiz < 70) {
            return CorBagagem.AMARELA;
        }
        if (matiz < 170) {
            return CorBagagem.VERDE;
        }
        if (matiz < 255) {
            return CorBagagem.AZUL;
        }
        if (matiz < 290) {
            return CorBagagem.ROXA;
        }
        return CorBagagem.ROSA;
    }

    private static BufferedImage reduzir(BufferedImage foto) {
        double escala = Math.min(1.0, (double) LADO_REDUZIDO / Math.max(foto.getWidth(), foto.getHeight()));
        int largura = Math.max(1, (int) Math.round(foto.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(foto.getHeight() * escala));
        BufferedImage reduzida = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = reduzida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(foto, 0, 0, largura, altura, null);
        g.dispose();
        return reduzida;
    }
}
