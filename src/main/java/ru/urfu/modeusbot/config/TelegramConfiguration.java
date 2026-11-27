package ru.urfu.modeusbot.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import javax.net.SocketFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory.DefaultOkHttpClientCreator;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "telegram.bot", name = "enabled", havingValue = "true")
public class TelegramConfiguration {

    @Bean
    TelegramClient telegramClient(TelegramBotProperties properties) {
        return new OkHttpTelegramClient(withPlainSockets(new OkHttpClient()), properties.token());
    }

    @Bean
    TelegramBotsLongPollingApplication telegramBotsApplication() {
        var defaultClient = new DefaultOkHttpClientCreator();
        return new TelegramBotsLongPollingApplication(ObjectMapper::new,
                () -> withPlainSockets(defaultClient.get()));
    }

    private OkHttpClient withPlainSockets(OkHttpClient client) {
        return client.newBuilder().socketFactory(new PlainSocketFactory()).build();
    }

    private static final class PlainSocketFactory extends SocketFactory {

        @Override
        public Socket createSocket() {
            return new Socket(Proxy.NO_PROXY);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return connect(new InetSocketAddress(host, port), null);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return connect(new InetSocketAddress(host, port), null);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return connect(new InetSocketAddress(host, port), new InetSocketAddress(localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) throws IOException {
            return connect(new InetSocketAddress(host, port), new InetSocketAddress(localHost, localPort));
        }

        private Socket connect(InetSocketAddress remote, InetSocketAddress local) throws IOException {
            Socket socket = createSocket();
            try {
                if (local != null) {
                    socket.bind(local);
                }
                socket.connect(remote);
                return socket;
            } catch (IOException exception) {
                try {
                    socket.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
                throw exception;
            }
        }
    }
}
