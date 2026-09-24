package com.marillia.milebag.business.mensagem.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.marillia.milebag.core.config.TelegramProperties;
import com.sun.net.httpserver.HttpServer;

/**
 * A Bot API documenta o token com ":" literal no caminho
 * ({@code /bot123:ABC/sendMessage}); um template de URI o codificaria como
 * %3A, o que nao da para provar aceito sem um bot real.
 */
class TelegramClientTest {

    private HttpServer servidor;
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<String> corpo = new AtomicReference<>();

    @BeforeEach
    void subirServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", troca -> {
            caminho.set(troca.getRequestURI().getRawPath());
            corpo.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resposta = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    private TelegramClient cliente(boolean habilitado) {
        return new TelegramClient(
                new TelegramProperties(habilitado, "123456:ABC-def_GHI", "milebag_bot", "segredo", "http://localhost:" + servidor.getAddress().getPort()));
    }

    @Test
    void enviaComOTokenLiteralNoCaminhoEOChatIdNoCorpo() {
        boolean enviado = cliente(true).enviar(777L, "Chego em 5 minutos");

        assertThat(enviado).isTrue();
        assertThat(caminho.get()).isEqualTo("/bot123456:ABC-def_GHI/sendMessage");
        assertThat(corpo.get()).contains("\"chat_id\":777").contains("\"text\":\"Chego em 5 minutos\"");
    }

    @Test
    void desabilitadoNaoChamaAApi() {
        boolean enviado = cliente(false).enviar(777L, "oi");

        assertThat(enviado).isFalse();
        assertThat(caminho.get()).isNull();
    }
}
