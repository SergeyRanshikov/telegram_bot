package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repositiry.NotificationTaskRepositiry;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    public static final Pattern PATTERN = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)([\\W+]+)");
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    @Autowired
    private TelegramBot telegramBot;

    @Autowired
    private NotificationTaskRepositiry repositiry;

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {

            String text = update.message().text();
            Long chatId = update.message().chat().id();
            Matcher matcher = PATTERN.matcher(text);

            if ("/start".equalsIgnoreCase(text)) {
                telegramBot.execute(new SendMessage(chatId, "Hello!"));
            } else if (matcher.matches()) {
                try {
                    String time = matcher.group(1);
                    String userText = matcher.group(3);
                    LocalDateTime exetDate = LocalDateTime.parse(time, FORMATTER);
                    NotificationTask task = new NotificationTask();
                    task.setChatId(chatId);
                    task.setText(userText);
                    task.setExecDate(exetDate);
                    repositiry.save(task);
                    telegramBot.execute(new SendMessage(chatId, "Событие сохранено"));
                } catch (DateTimeParseException e) {
                    telegramBot.execute(new SendMessage(chatId, "Неверный фомат даты и времени"));
                }
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    @Scheduled(fixedDelay = 60_000L)
    public void schedule() {
        List<NotificationTask> tasks = repositiry.findAllByExecDateLessThan(LocalDateTime.now());
        tasks.forEach(t -> {
            SendResponse response = telegramBot.execute(new SendMessage(t.getChatId(), t.getText()));
            if(response.isOk()) {
                repositiry.delete(t);
            }
        });
    }
}