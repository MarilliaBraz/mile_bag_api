package com.marillia.milebag.core.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.marillia.milebag.core.config.ValhallaProperties;
import com.sun.net.httpserver.HttpServer;

/** Distingue "nao ha caminho" (o motor respondeu) de "motor fora do ar" - com um servidor HTTP local no lugar do Valhalla. */
class RoutingClientTest {

    private static final Coordenada ORIGEM = new Coordenada(-16.68, -49.25);
    private static final Coordenada DESTINO = new Coordenada(-16.70, -49.30);

    private HttpServer servidor;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void semCaminhoEntreOsPontosMantemOMotorDisponivel() throws IOException {
        RoutingClient cliente = clienteRespondendo(400, "{\"error_code\":442,\"error\":\"No path could be found for input\"}");

        RoutingClient.ResultadoRotas resultado = cliente.calcularAlternativasComTracado(ORIGEM, DESTINO);

        assertThat(resultado.motorDisponivel()).isTrue();
        assertThat(resultado.alternativas()).isEmpty();
    }

    @Test
    void erroInternoDoMotorContaComoIndisponivel() throws IOException {
        RoutingClient cliente = clienteRespondendo(503, "{}");

        RoutingClient.ResultadoRotas resultado = cliente.calcularAlternativasComTracado(ORIGEM, DESTINO);

        assertThat(resultado.motorDisponivel()).isFalse();
        assertThat(resultado.alternativas()).isEmpty();
    }

    @Test
    void motorForaDoArContaComoIndisponivel() throws IOException {
        int portaLivre;
        try (ServerSocket socket = new ServerSocket(0)) {
            portaLivre = socket.getLocalPort();
        }
        RoutingClient cliente = new RoutingClient(new ValhallaProperties("http://localhost:" + portaLivre));

        RoutingClient.ResultadoRotas resultado = cliente.calcularAlternativasComTracado(ORIGEM, DESTINO);

        assertThat(resultado.motorDisponivel()).isFalse();
        assertThat(resultado.alternativas()).isEmpty();
    }

    private RoutingClient clienteRespondendo(int status, String corpo) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/route", troca -> {
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        servidor.start();
        return new RoutingClient(new ValhallaProperties("http://localhost:" + servidor.getAddress().getPort()));
    }
}
