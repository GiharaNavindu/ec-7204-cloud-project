package com.gihara.notificationservice.exception;

public class NotificationAccessDeniedException extends RuntimeException {

    public NotificationAccessDeniedException(String message) {
        super(message);
    }
}
