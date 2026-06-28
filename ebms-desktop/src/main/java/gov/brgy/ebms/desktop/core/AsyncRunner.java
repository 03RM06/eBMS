package gov.brgy.ebms.desktop.core;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AsyncRunner {

    private static final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private AsyncRunner() {}

    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, Dialogs::handleApiError);
    }

    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> onSuccess.accept(task.getValue())));
        task.setOnFailed(e -> Platform.runLater(() -> onError.accept(task.getException())));
        pool.submit(task);
    }

    public static void runVoid(Runnable work, Runnable onSuccess) {
        runVoid(work, onSuccess, Dialogs::handleApiError);
    }

    public static void runVoid(Runnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        run(() -> { work.run(); return null; }, ignored -> onSuccess.run(), onError);
    }
}
