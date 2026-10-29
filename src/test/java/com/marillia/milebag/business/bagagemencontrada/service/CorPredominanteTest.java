package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;

/** Fotos sinteticas: a bagagem no centro, o chao em volta. */
class CorPredominanteTest {

    @Test
    void malaNoCentroVenceOChaoEmVolta() {
        assertThat(CorPredominante.sugerir(mala(new Color(200, 30, 35), new Color(120, 120, 120)))).contains(CorBagagem.VERMELHA);
    }

    @Test
    void reconheceAsCoresMaisComunsDeMala() {
        Color chao = new Color(235, 235, 230);
        assertThat(CorPredominante.sugerir(mala(new Color(25, 25, 28), chao))).contains(CorBagagem.PRETA);
        assertThat(CorPredominante.sugerir(mala(new Color(20, 35, 95), chao))).contains(CorBagagem.AZUL);
        assertThat(CorPredominante.sugerir(mala(new Color(110, 70, 40), chao))).contains(CorBagagem.MARROM);
        assertThat(CorPredominante.sugerir(mala(new Color(220, 200, 160), chao))).contains(CorBagagem.BEGE);
        assertThat(CorPredominante.sugerir(mala(new Color(130, 130, 135), chao))).contains(CorBagagem.CINZA);
        assertThat(CorPredominante.sugerir(mala(new Color(40, 140, 60), chao))).contains(CorBagagem.VERDE);
        assertThat(CorPredominante.sugerir(mala(new Color(230, 110, 170), chao))).contains(CorBagagem.ROSA);
    }

    @Test
    void semCorDominanteNaoSugere() {
        BufferedImage listras = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = listras.createGraphics();
        Color[] cores = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, new Color(128, 0, 160)};
        for (int i = 0; i < 25; i++) {
            g.setColor(cores[i % cores.length]);
            g.fillRect(i * 20, 0, 20, 500);
        }
        g.dispose();

        assertThat(CorPredominante.sugerir(listras)).isEmpty();
    }

    private static BufferedImage mala(Color mala, Color chao) {
        BufferedImage foto = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = foto.createGraphics();
        g.setColor(chao);
        g.fillRect(0, 0, 1200, 900);
        g.setColor(mala);
        g.fillRoundRect(330, 180, 540, 560, 60, 60);
        g.dispose();
        return foto;
    }
}
