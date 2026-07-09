package com.kaio.runtracker.service;

public class OpenAiTrainingPlanException extends RuntimeException {

    public OpenAiTrainingPlanException(String message) {
        super(message);
    }

    public OpenAiTrainingPlanException(String message, Throwable cause) {
        super(message, cause);
    }
}
