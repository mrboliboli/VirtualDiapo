package fr.virtualdiapo.desktop.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.IntConsumer;

final class SlideGridView extends ScrollPane {
    private final ObservableList<Path> items;
    private final TilePane tiles = new TilePane(16, 16);
    private final ToggleGroup selection = new ToggleGroup();
    private final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);
    private final Image normalMount;
    private final Image selectedMount;
    private IntConsumer selectionHandler = ignored -> { };
    private int draggedIndex = -1;
    private boolean mutating;

    SlideGridView(ObservableList<Path> items, Image normalMount, Image selectedMount) {
        this.items = Objects.requireNonNull(items);
        this.normalMount = Objects.requireNonNull(normalMount);
        this.selectedMount = Objects.requireNonNull(selectedMount);
        getStyleClass().add("slide-grid-scroll");
        tiles.getStyleClass().add("slide-grid");
        tiles.setPadding(new Insets(2));
        tiles.setPrefTileWidth(184);
        tiles.setPrefTileHeight(224);
        setContent(tiles);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        items.addListener((ListChangeListener<Path>) ignored -> {
            if (!mutating) rebuild();
        });
        rebuild();
    }

    ReadOnlyIntegerProperty selectedIndexProperty() {
        return selectedIndex;
    }

    int getSelectedIndex() {
        return selectedIndex.get();
    }

    void setOnSelection(IntConsumer handler) {
        selectionHandler = Objects.requireNonNull(handler);
    }

    void select(int index) {
        if (index < 0 || index >= tiles.getChildren().size()) {
            selection.selectToggle(null);
            selectedIndex.set(-1);
            selectionHandler.accept(-1);
            return;
        }
        var card = (ToggleButton) tiles.getChildren().get(index);
        selection.selectToggle(card);
        selectedIndex.set(index);
        updateMounts();
        selectionHandler.accept(index);
        card.requestFocus();
    }

    private void rebuild() {
        var previous = Math.min(selectedIndex.get(), items.size() - 1);
        selection.getToggles().clear();
        tiles.getChildren().setAll(java.util.stream.IntStream.range(0, items.size())
                .mapToObj(this::createCard).toList());
        if (previous >= 0) select(previous);
        else {
            selectedIndex.set(-1);
            selectionHandler.accept(-1);
        }
    }

    private ToggleButton createCard(int index) {
        var path = items.get(index);
        var photo = new ImageView();
        photo.setFitWidth(66);
        photo.setFitHeight(66);
        photo.setPreserveRatio(false);
        photo.setSmooth(true);
        setSquareImage(photo, new Image(path.toUri().toString(), true));
        var mount = new ImageView(normalMount);
        mount.setFitWidth(164);
        mount.setFitHeight(164);
        mount.setPreserveRatio(true);
        mount.setSmooth(true);
        var composition = new StackPane(mount, photo);
        composition.getStyleClass().add("slide-composition");
        var number = new Label(Integer.toString(index + 1));
        number.getStyleClass().add("slide-number");
        var content = new VBox(10, composition, number);
        content.setAlignment(Pos.TOP_CENTER);

        var card = new ToggleButton();
        card.getStyleClass().add("slide-card");
        card.setToggleGroup(selection);
        card.setGraphic(content);
        card.setUserData(index);
        card.setAccessibleText("Diapositive " + (index + 1) + ", " + path.getFileName());
        card.setTooltip(new Tooltip(path.getFileName().toString()));
        card.setOnAction(event -> select((Integer) card.getUserData()));
        card.setOnKeyPressed(event -> {
            var indexOnCard = (Integer) card.getUserData();
            if (event.isAltDown() && (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.LEFT)) {
                select(indexOnCard);
                moveSelected(-1);
                event.consume();
            } else if (event.isAltDown() && (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.RIGHT)) {
                select(indexOnCard);
                moveSelected(1);
                event.consume();
            } else if (!event.isAltDown()) {
                var horizontalPadding = tiles.getPadding().getLeft() + tiles.getPadding().getRight();
                var columns = Math.max(1, (int) ((tiles.getWidth() - horizontalPadding + tiles.getHgap())
                        / (tiles.getPrefTileWidth() + tiles.getHgap())));
                var target = switch (event.getCode()) {
                    case LEFT -> indexOnCard - 1;
                    case RIGHT -> indexOnCard + 1;
                    case UP -> indexOnCard - columns;
                    case DOWN -> indexOnCard + columns;
                    case HOME -> 0;
                    case END -> items.size() - 1;
                    default -> -1;
                };
                if (target >= 0 && target < items.size()) {
                    select(target);
                    event.consume();
                }
            }
        });
        card.setOnDragDetected(event -> {
            draggedIndex = (Integer) card.getUserData();
            var contentData = new ClipboardContent();
            contentData.putString(Integer.toString(draggedIndex));
            card.startDragAndDrop(TransferMode.MOVE).setContent(contentData);
            card.getStyleClass().add("dragging");
            event.consume();
        });
        card.setOnDragOver(event -> {
            if (draggedIndex >= 0 && draggedIndex != (Integer) card.getUserData()) {
                event.acceptTransferModes(TransferMode.MOVE);
                card.getStyleClass().add("drop-target");
                event.consume();
            }
        });
        card.setOnDragExited(event -> card.getStyleClass().remove("drop-target"));
        card.setOnDragDropped(event -> {
            var target = (Integer) card.getUserData();
            event.setDropCompleted(event.getDragboard().hasString() && move(draggedIndex, target));
            event.consume();
        });
        card.setOnDragDone(event -> {
            draggedIndex = -1;
            card.getStyleClass().removeAll("dragging", "drop-target");
        });
        return card;
    }

    private void moveSelected(int offset) {
        move(selectedIndex.get(), selectedIndex.get() + offset);
    }

    private boolean move(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size() || from == to) return false;
        mutating = true;
        try {
            var image = items.remove(from);
            items.add(to, image);
        } finally {
            mutating = false;
        }
        rebuild();
        select(to);
        return true;
    }

    private void updateMounts() {
        for (var node : tiles.getChildren()) {
            var card = (ToggleButton) node;
            var box = (VBox) card.getGraphic();
            var composition = (StackPane) box.getChildren().getFirst();
            var mount = (ImageView) composition.getChildren().getFirst();
            mount.setImage(card.isSelected() ? selectedMount : normalMount);
        }
    }

    private static void setSquareImage(ImageView view, Image image) {
        view.setImage(image);
        Runnable crop = () -> {
            if (image.getWidth() <= 0 || image.getHeight() <= 0) return;
            var side = Math.min(image.getWidth(), image.getHeight());
            view.setViewport(new Rectangle2D(
                    (image.getWidth() - side) / 2,
                    (image.getHeight() - side) / 2,
                    side,
                    side));
        };
        image.progressProperty().addListener((ignored, oldValue, progress) -> {
            if (progress.doubleValue() >= 1) crop.run();
        });
        crop.run();
    }
}
