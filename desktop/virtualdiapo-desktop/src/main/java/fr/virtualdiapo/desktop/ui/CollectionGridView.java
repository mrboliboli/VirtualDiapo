package fr.virtualdiapo.desktop.ui;

import fr.virtualdiapo.core.SlideCollection;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

final class CollectionGridView extends ScrollPane {
    private final ObservableList<SlideCollection> items;
    private final Function<SlideCollection, Path> coverPath;
    private final TilePane tiles = new TilePane(12, 12);
    private final ToggleGroup selection = new ToggleGroup();
    private final ObjectProperty<SlideCollection> selectedItem = new SimpleObjectProperty<>();
    private Consumer<SlideCollection> selectionHandler = ignored -> { };

    CollectionGridView(ObservableList<SlideCollection> items, Function<SlideCollection, Path> coverPath) {
        this.items = Objects.requireNonNull(items);
        this.coverPath = Objects.requireNonNull(coverPath);
        getStyleClass().add("collection-grid-scroll");
        tiles.getStyleClass().add("collection-grid");
        tiles.setPadding(new Insets(1));
        tiles.setPrefColumns(2);
        tiles.setPrefTileWidth(126);
        tiles.setPrefTileHeight(148);
        setContent(tiles);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        items.addListener((ListChangeListener<SlideCollection>) ignored -> rebuild());
        widthProperty().addListener((ignored, oldWidth, width) -> updateTileWidth(width.doubleValue()));
        rebuild();
    }

    ReadOnlyObjectProperty<SlideCollection> selectedItemProperty() {
        return selectedItem;
    }

    void setOnSelection(Consumer<SlideCollection> handler) {
        selectionHandler = Objects.requireNonNull(handler);
    }

    void select(SlideCollection collection) {
        if (collection == null) {
            selection.selectToggle(null);
            selectedItem.set(null);
            return;
        }
        tiles.getChildren().stream()
                .filter(ToggleButton.class::isInstance)
                .map(ToggleButton.class::cast)
                .filter(button -> Objects.equals(button.getUserData(), collection))
                .findFirst()
                .ifPresent(button -> selection.selectToggle(button));
        selectedItem.set(collection);
    }

    private void rebuild() {
        var previous = selectedItem.get();
        selection.getToggles().clear();
        tiles.getChildren().setAll(items.stream().map(this::createCard).toList());
        select(previous);
    }

    private ToggleButton createCard(SlideCollection collection) {
        var cover = new ImageView();
        cover.setFitWidth(108);
        cover.setFitHeight(74);
        cover.setSmooth(true);
        if (!collection.slides().isEmpty()) {
            setCoverImage(cover, new Image(coverPath.apply(collection).toUri().toString(), true));
        }
        var photoFrame = new StackPane(cover);
        photoFrame.getStyleClass().add("collection-cover-frame");
        photoFrame.setMinSize(118, 84);
        photoFrame.setPrefSize(118, 84);

        var title = new Label(collection.title());
        title.getStyleClass().add("collection-card-title");
        title.setMaxWidth(116);
        title.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        var details = collection.slides().size()
                + (collection.slides().size() > 1 ? " diapositives" : " diapositive")
                + (collection.year() == null ? "" : " · " + collection.year());
        var metadata = new Label(details);
        metadata.getStyleClass().add("collection-card-metadata");
        metadata.setMaxWidth(116);
        var content = new VBox(7, photoFrame, title, metadata);
        content.setAlignment(Pos.TOP_LEFT);

        var card = new ToggleButton();
        card.getStyleClass().add("collection-card");
        card.setToggleGroup(selection);
        card.setUserData(collection);
        card.setGraphic(content);
        card.setAccessibleText(collection.title() + ", " + details);
        card.setOnAction(event -> {
            activate(items.indexOf(collection));
        });
        card.setOnKeyPressed(event -> {
            var index = items.indexOf(collection);
            var columns = Math.max(1, (int) ((tiles.getWidth() + tiles.getHgap())
                    / (tiles.getPrefTileWidth() + tiles.getHgap())));
            var target = switch (event.getCode()) {
                case LEFT -> index - 1;
                case RIGHT -> index + 1;
                case UP -> index - columns;
                case DOWN -> index + columns;
                case HOME -> 0;
                case END -> items.size() - 1;
                default -> -1;
            };
            if (target >= 0 && target < items.size()) {
                activate(target);
                event.consume();
            }
        });
        return card;
    }

    private void activate(int index) {
        if (index < 0 || index >= items.size()) return;
        var collection = items.get(index);
        select(collection);
        ((ToggleButton) tiles.getChildren().get(index)).requestFocus();
        selectionHandler.accept(collection);
    }

    private void updateTileWidth(double width) {
        var available = Math.max(252, width - 20);
        tiles.setPrefTileWidth(Math.max(126, Math.min(170, (available - 12) / 2)));
    }

    private static void setCoverImage(ImageView view, Image image) {
        view.setImage(image);
        Runnable crop = () -> {
            if (image.getWidth() <= 0 || image.getHeight() <= 0) return;
            var targetRatio = 108d / 74d;
            var imageRatio = image.getWidth() / image.getHeight();
            if (imageRatio > targetRatio) {
                var width = image.getHeight() * targetRatio;
                view.setViewport(new Rectangle2D((image.getWidth() - width) / 2, 0, width, image.getHeight()));
            } else {
                var height = image.getWidth() / targetRatio;
                view.setViewport(new Rectangle2D(0, (image.getHeight() - height) / 2, image.getWidth(), height));
            }
        };
        image.progressProperty().addListener((ignored, oldValue, progress) -> {
            if (progress.doubleValue() >= 1) crop.run();
        });
        crop.run();
    }
}
