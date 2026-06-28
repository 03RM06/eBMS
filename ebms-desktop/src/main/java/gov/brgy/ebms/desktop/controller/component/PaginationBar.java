package gov.brgy.ebms.desktop.controller.component;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

public class PaginationBar extends HBox {

    private final Button first;
    private final Button prev;
    private final Button next;
    private final Button last;
    private final Label pageLabel;

    private int currentPage = 0;
    private int totalPages = 0;
    private Consumer<Integer> onPageChange;

    public PaginationBar() {
        setSpacing(8);
        setPadding(new Insets(4, 0, 4, 0));

        first = new Button("|<");
        prev = new Button("<");
        next = new Button(">");
        last = new Button(">|");
        pageLabel = new Label("Page 1 of 1");

        first.setOnAction(e -> { if (onPageChange != null) onPageChange.accept(0); });
        prev.setOnAction(e -> { if (onPageChange != null && currentPage > 0) onPageChange.accept(currentPage - 1); });
        next.setOnAction(e -> { if (onPageChange != null && currentPage < totalPages - 1) onPageChange.accept(currentPage + 1); });
        last.setOnAction(e -> { if (onPageChange != null && totalPages > 0) onPageChange.accept(totalPages - 1); });

        getChildren().addAll(first, prev, pageLabel, next, last);
        updateButtons();
    }

    public void update(int page, int total) {
        this.currentPage = page;
        this.totalPages = Math.max(total, 1);
        pageLabel.setText("Page " + (page + 1) + " of " + totalPages);
        updateButtons();
    }

    private void updateButtons() {
        first.setDisable(currentPage == 0);
        prev.setDisable(currentPage == 0);
        next.setDisable(currentPage >= totalPages - 1);
        last.setDisable(currentPage >= totalPages - 1);
    }

    public void setOnPageChange(Consumer<Integer> cb) {
        this.onPageChange = cb;
    }

    public int getCurrentPage() { return currentPage; }
    public int getTotalPages() { return totalPages; }
}
