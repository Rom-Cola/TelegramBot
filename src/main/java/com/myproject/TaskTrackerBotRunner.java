package com.myproject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


public class TaskTrackerBotRunner {
    private static final Logger logger = LoggerFactory.getLogger(TaskTrackerBotRunner.class);
    public static void main(String[] args) throws TelegramApiException {

        try {
            logger.info("Starting bot");
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new TaskTrackerBot());
            logger.info("Bot started successfully");
        } catch (TelegramApiException e) {
            logger.error("Error starting bot", e);
        }
    }

}