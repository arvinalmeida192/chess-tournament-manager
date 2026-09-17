package com.chess.tournament.ui.util;

import com.chess.tournament.exception.DomainException;

/**
 * Unwraps JavaFX {@link javafx.concurrent.Task} failures so hosts see the domain message.
 */
public final class TaskExceptions {

    private TaskExceptions() {
    }

    public static Throwable root(Throwable error) {
        if (error == null) {
            return null;
        }
        Throwable walk = error;
        Throwable domain = null;
        while (walk != null) {
            if (walk instanceof DomainException) {
                domain = walk;
            }
            if (walk.getCause() == null || walk.getCause() == walk) {
                break;
            }
            walk = walk.getCause();
        }
        return domain != null ? domain : walk;
    }

    public static String message(Throwable error) {
        Throwable root = root(error);
        if (root == null) {
            return "Unknown error";
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
