package ru.urfu.modeusbot.telegram;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.urfu.modeusbot.config.TelegramBotProperties;
import ru.urfu.modeusbot.modeus.ModeusException;
import ru.urfu.modeusbot.telegram.command.CommandDispatcher;

@Component
@ConditionalOnProperty(prefix = "telegram.bot", name = "enabled", havingValue = "true")
public class ModeusTelegramBot implements SpringLongPollingBot, LongPollingUpdateConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModeusTelegramBot.class);
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private final TelegramBotProperties properties;
    private final TelegramClient telegramClient;
    private final CommandDispatcher dispatcher;

    public ModeusTelegramBot(TelegramBotProperties properties, TelegramClient telegramClient,
                             CommandDispatcher dispatcher) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.dispatcher = dispatcher;
    }

    @Override
    public String getBotToken() {
        return properties.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @AfterBotRegistration
    public void afterRegistration() {
        LOGGER.info("Telegram-бот подключён через long polling.");
    }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            try {
                handle(update);
            } catch (TelegramApiException exception) {
                LOGGER.warn("Не удалось отправить ответ Telegram. Проверьте доступность сервиса и доступ к чату.");
            } catch (RuntimeException exception) {
                LOGGER.error("Не удалось обработать сообщение Telegram.");
            }
        }
    }

    private void handle(Update update) throws TelegramApiException {
        if (update == null || !update.hasMessage()) {
            return;
        }
        var message = update.getMessage();
        var sender = message.getFrom();
        if (!"private".equals(message.getChat().getType()) || sender == null || sender.getId() == null
                || Boolean.TRUE.equals(sender.getIsBot())) {
            return;
        }
        if (message.hasText() && "/id".equalsIgnoreCase(message.getText().strip())) {
            send(message.getChatId(), "Твой Telegram ID: " + sender.getId());
            return;
        }
        if (!sender.getId().equals(properties.ownerId())) {
            return;
        }
        if (!message.hasText()) {
            send(message.getChatId(), "Пока понимаю только текст. Список команд: /help.");
            return;
        }
        try {
            var response = dispatcher.dispatch(message.getText());
            if (response.isPresent()) {
                send(message.getChatId(), response.get());
            }
        } catch (ModeusException exception) {
            LOGGER.warn("Не удалось получить расписание Modeus: {}", exception.getMessage());
            send(message.getChatId(), "Не удалось получить расписание из Modeus. Проверь подключение и попробуй позже.");
        }
    }

    private void send(Long chatId, String text) throws TelegramApiException {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_MESSAGE_LENGTH, text.length());
            if (end < text.length()) {
                int lineBreak = text.lastIndexOf('\n', end - 1);
                if (lineBreak > start) {
                    end = lineBreak + 1;
                } else if (Character.isSurrogatePair(text.charAt(end - 1), text.charAt(end))) {
                    end--;
                }
            }
            String part = text.substring(start, end).strip();
            if (!part.isEmpty()) {
                telegramClient.execute(SendMessage.builder().chatId(chatId).text(part).build());
            }
            start = end;
        }
    }
}
