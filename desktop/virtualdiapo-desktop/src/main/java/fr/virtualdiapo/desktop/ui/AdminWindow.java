package fr.virtualdiapo.desktop.ui;

import fr.virtualdiapo.core.CollectionCatalog;
import fr.virtualdiapo.core.SlideCollection;
import fr.virtualdiapo.desktop.VirtualDiapoApplication;
import fr.virtualdiapo.desktop.catalog.JpegCollectionImporter;
import fr.virtualdiapo.desktop.catalog.CollectionManagementService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AdminWindow extends Application {
    private ConfigurableApplicationContext context;
    private CollectionCatalog catalog;
    private JpegCollectionImporter importer;
    private CollectionManagementService management;
    private SlideCollection editedCollection;

    private final ObservableList<SlideCollection> collections = FXCollections.observableArrayList();
    private final ObservableList<Path> selectedImages = FXCollections.observableArrayList();
    private final ListView<SlideCollection> collectionList = new ListView<>(collections);
    private final ListView<Path> imageList = new ListView<>(selectedImages);
    private final ImageView preview = new ImageView();
    private final TextField title = new TextField();
    private final TextArea description = new TextArea();
    private final TextField year = new TextField();
    private final Label status = new Label();
    private final Label imageCount = new Label("0 image");
    private final Label editorHeading = new Label("Nouveau carrousel");
    private final Label editorMetadata = new Label("Préparez une nouvelle collection de diapositives");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button saveButton = new Button("Enregistrer");
    private final Button newButton = new Button("+  Nouveau carrousel");
    private final Button resetButton = new Button("Nouvelle");
    private final Button chooseButton = new Button("Ajouter des JPEG…");
    private final Button upButton = new Button("Monter");
    private final Button downButton = new Button("Descendre");
    private final Button removeButton = new Button("Retirer");
    private final Button deleteButton = new Button("Supprimer");
    private int draggedImageIndex = -1;

    @Override
    public void init() {
        context = SpringApplication.run(VirtualDiapoApplication.class);
        catalog = context.getBean(CollectionCatalog.class);
        importer = context.getBean(JpegCollectionImporter.class);
        management = context.getBean(CollectionManagementService.class);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("VirtualDiapo — Collections");
        stage.setMinWidth(980);
        stage.setMinHeight(650);
        var scene = new Scene(buildRoot(stage), 1180, 760);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/virtualdiapo.css")).toExternalForm());
        stage.setScene(scene);
        stage.show();
        refreshCollections();
    }

    private BorderPane buildRoot(Stage stage) {
        var root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setLeft(buildSidebar());
        var workspace = new HBox(18, buildCatalogPane(), buildImportPane(stage));
        workspace.getStyleClass().add("workspace");
        HBox.setHgrow(workspace.getChildren().get(1), Priority.ALWAYS);
        root.setCenter(workspace);
        return root;
    }

    private VBox buildSidebar() {
        var wordmark = new Label("VIRTUALDIAPO");
        wordmark.getStyleClass().add("wordmark");

        var navigation = new Label("Carrousels");
        navigation.getStyleClass().add("navigation-item-active");
        navigation.setMaxWidth(Double.MAX_VALUE);

        var spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        var serverDot = new Label("●");
        serverDot.getStyleClass().add("server-dot");
        var serverStatus = new Label("Serveur actif");
        serverStatus.getStyleClass().add("server-status");
        var statusLine = new HBox(7, serverDot, serverStatus);
        statusLine.setAlignment(Pos.CENTER_LEFT);
        var serverName = new Label("VirtualDiapo");
        var serverAddress = new Label("localhost:8080");
        serverName.getStyleClass().add("sidebar-detail");
        serverAddress.getStyleClass().add("sidebar-detail");

        var sidebar = new VBox(28, wordmark, navigation, spacer, statusLine, serverName, serverAddress);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(166);
        sidebar.setMinWidth(150);
        return sidebar;
    }

    private VBox buildCatalogPane() {
        var heading = new Label("Mes carrousels");
        heading.getStyleClass().add("section-heading");
        var subtitle = new Label("Organisez vos collections de diapositives");
        subtitle.getStyleClass().add("section-subtitle");
        var titles = new VBox(2, heading, subtitle);
        newButton.getStyleClass().add("primary-button");
        newButton.setOnAction(event -> clearForm());
        newButton.setMaxWidth(Double.MAX_VALUE);
        var header = new VBox(10, titles, newButton);
        collectionList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(SlideCollection item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : createCollectionCellContent(item));
            }
        });
        collectionList.getStyleClass().add("collection-list");
        collectionList.getSelectionModel().selectedItemProperty().addListener((ignored, oldValue, selected) -> {
            if (selected != null) {
                edit(selected);
            }
        });
        VBox.setVgrow(collectionList, Priority.ALWAYS);
        var pane = new VBox(16, header, collectionList);
        pane.getStyleClass().addAll("surface-card", "catalog-card");
        pane.setPrefWidth(320);
        pane.setMinWidth(260);
        return pane;
    }

    private HBox createCollectionCellContent(SlideCollection collection) {
        var thumbnail = new ImageView();
        thumbnail.setFitWidth(70);
        thumbnail.setFitHeight(54);
        thumbnail.setPreserveRatio(true);
        thumbnail.setSmooth(true);
        if (!collection.slides().isEmpty()) {
            var path = importer.storedImagePath(collection.slides().getFirst().id());
            thumbnail.setImage(new Image(path.toUri().toString(), 140, 108, false, true, true));
        }
        var thumbnailFrame = new StackPane(thumbnail);
        thumbnailFrame.getStyleClass().add("slide-thumbnail");
        var name = new Label(collection.title());
        name.getStyleClass().add("collection-title");
        name.setWrapText(true);
        name.setMaxWidth(155);
        var details = collection.slides().size() + (collection.slides().size() > 1 ? " diapositives" : " diapositive")
                + (collection.year() == null ? "" : "  ·  " + collection.year());
        var metadata = new Label(details);
        metadata.getStyleClass().add("collection-metadata");
        var labels = new VBox(5, name, metadata);
        labels.setAlignment(Pos.CENTER_LEFT);
        var content = new HBox(12, thumbnailFrame, labels);
        content.setAlignment(Pos.CENTER_LEFT);
        return content;
    }

    private VBox buildImportPane(Stage stage) {
        editorHeading.getStyleClass().add("section-heading");
        editorMetadata.getStyleClass().add("section-subtitle");
        var editorTitles = new VBox(2, editorHeading, editorMetadata);
        title.setPromptText("Titre");
        title.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= SlideCollection.MAX_TITLE_LENGTH ? change : null));
        description.setPromptText("Description (facultative)");
        description.setPrefRowCount(2);
        year.setPromptText("Année (facultative)");

        var titleField = labelledField("Titre", title);
        var yearField = labelledField("Année", year);
        yearField.setPrefWidth(130);
        var firstRow = new HBox(12, titleField, yearField);
        HBox.setHgrow(titleField, Priority.ALWAYS);
        var descriptionField = labelledField("Description", description);

        preview.setFitWidth(180);
        preview.setFitHeight(150);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        var previewBox = new BorderPane(preview);
        previewBox.setMinHeight(170);
        previewBox.getStyleClass().add("preview-box");
        BorderPane.setAlignment(preview, Pos.CENTER);

        imageList.setCellFactory(ignored -> createImageCell());
        imageList.getSelectionModel().selectedItemProperty().addListener((ignored, oldValue, path) -> {
            showPreview(path);
            updateImageActions();
        });
        selectedImages.addListener((javafx.collections.ListChangeListener<Path>) ignored -> {
            updateImageCount();
            updateImageActions();
        });
        updateImageCount();
        updateImageActions();
        VBox.setVgrow(imageList, Priority.ALWAYS);

        chooseButton.setOnAction(event -> chooseImages(stage));
        upButton.setOnAction(event -> moveSelected(-1));
        downButton.setOnAction(event -> moveSelected(1));
        removeButton.setOnAction(event -> removeSelected());
        chooseButton.getStyleClass().add("add-button");
        upButton.getStyleClass().add("secondary-button");
        downButton.getStyleClass().add("secondary-button");
        removeButton.getStyleClass().add("danger-button");
        imageCount.getStyleClass().add("count-chip");
        var imageActions = new HBox(8, chooseButton, upButton, downButton, removeButton, imageCount);
        imageActions.setAlignment(Pos.CENTER_LEFT);

        saveButton.setDefaultButton(true);
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(event -> saveCollection());
        resetButton.setOnAction(event -> clearForm());
        resetButton.setText("Réinitialiser");
        resetButton.getStyleClass().add("secondary-button");
        deleteButton.setDisable(true);
        deleteButton.setOnAction(event -> deleteCollection());
        deleteButton.getStyleClass().add("danger-button");
        status.setWrapText(true);
        status.getStyleClass().add("operation-status");
        progress.setMaxSize(22, 22);
        progress.setVisible(false);
        progress.setManaged(false);
        var footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        var footer = new HBox(10, deleteButton, status, progress, footerSpacer, resetButton, saveButton);
        footer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(status, Priority.ALWAYS);

        var listPane = new VBox(imageList);
        HBox.setHgrow(listPane, Priority.ALWAYS);
        var mediaPane = new HBox(14, listPane, previewBox);
        HBox.setHgrow(listPane, Priority.ALWAYS);
        VBox.setVgrow(mediaPane, Priority.ALWAYS);
        previewBox.setPrefWidth(190);
        var pane = new VBox(14, editorTitles, firstRow, descriptionField,
                new Separator(Orientation.HORIZONTAL), imageActions, mediaPane, footer);
        pane.getStyleClass().addAll("surface-card", "editor-card");
        pane.setMinWidth(400);
        return pane;
    }

    private VBox labelledField(String labelText, javafx.scene.Node field) {
        var label = new Label(labelText);
        label.getStyleClass().add("field-label");
        label.setLabelFor(field);
        var box = new VBox(6, label, field);
        VBox.setVgrow(field, Priority.ALWAYS);
        return box;
    }

    private ListCell<Path> createImageCell() {
        var cell = new ListCell<Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%02d    %s", getIndex() + 1, item.getFileName()));
            }
        };
        cell.setOnDragDetected(event -> {
            if (!cell.isEmpty()) {
                draggedImageIndex = cell.getIndex();
                var content = new ClipboardContent();
                content.putString(Integer.toString(draggedImageIndex));
                cell.startDragAndDrop(TransferMode.MOVE).setContent(content);
                event.consume();
            }
        });
        cell.setOnDragOver(event -> {
            if (draggedImageIndex >= 0 && draggedImageIndex != cell.getIndex()) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        cell.setOnDragDropped(event -> {
            int target = cell.isEmpty() ? selectedImages.size() - 1 : cell.getIndex();
            if (draggedImageIndex >= 0 && target >= 0 && target < selectedImages.size()) {
                var image = selectedImages.remove(draggedImageIndex);
                selectedImages.add(target, image);
                imageList.getSelectionModel().select(target);
                event.setDropCompleted(true);
            }
            draggedImageIndex = -1;
            event.consume();
        });
        cell.setOnDragDone(event -> draggedImageIndex = -1);
        return cell;
    }

    private void chooseImages(Stage stage) {
        var chooser = new FileChooser();
        chooser.setTitle("Choisir les diapositives JPEG");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images JPEG", "*.jpg", "*.jpeg", "*.JPG", "*.JPEG"));
        var files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            var additions = files.stream().map(java.io.File::toPath)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> selectedImages.stream().noneMatch(existing -> existing.equals(path)))
                    .toList();
            selectedImages.addAll(additions);
            if (!additions.isEmpty()) {
                imageList.getSelectionModel().select(selectedImages.size() - additions.size());
            } else {
                status.setText("Ces images sont déjà présentes dans la collection.");
            }
        }
    }

    private void moveSelected(int offset) {
        int from = imageList.getSelectionModel().getSelectedIndex();
        int to = from + offset;
        if (from >= 0 && to >= 0 && to < selectedImages.size()) {
            var image = selectedImages.remove(from);
            selectedImages.add(to, image);
            imageList.getSelectionModel().select(to);
        }
    }

    private void removeSelected() {
        int index = imageList.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            selectedImages.remove(index);
            if (!selectedImages.isEmpty()) {
                imageList.getSelectionModel().select(Math.min(index, selectedImages.size() - 1));
            } else {
                preview.setImage(null);
            }
        }
    }

    private void showPreview(Path path) {
        preview.setImage(path == null ? null : new Image(path.toUri().toString(), true));
    }

    private void saveCollection() {
        final Integer parsedYear;
        try {
            parsedYear = year.getText().isBlank() ? null : Integer.valueOf(year.getText().trim());
        } catch (NumberFormatException exception) {
            status.setText("L’année doit être un nombre.");
            year.requestFocus();
            return;
        }
        if (title.getText().isBlank()) {
            status.setText("Le titre est obligatoire.");
            title.requestFocus();
            return;
        }
        if (selectedImages.isEmpty()) {
            status.setText("Sélectionnez au moins une image JPEG.");
            return;
        }
        var files = List.copyOf(selectedImages);
        var savedTitle = title.getText();
        var savedDescription = description.getText();
        var editedId = editedCollection == null ? null : editedCollection.id();
        var task = new Task<SlideCollection>() {
            @Override
            protected SlideCollection call() throws Exception {
                if (editedId != null) {
                    return importer.updateFiles(
                            editedId, savedTitle, savedDescription, parsedYear, files);
                }
                return importer.importFiles(savedTitle, savedDescription, parsedYear, files);
            }
        };
        setBusy(true);
        status.setText(editedId == null ? "Création de la collection…" : "Enregistrement des modifications…");
        task.setOnSucceeded(event -> {
            setBusy(false);
            var saved = task.getValue();
            refreshCollections(saved.id());
            status.setText("Collection « " + saved.title() + " » enregistrée.");
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showOperationError("L’enregistrement a échoué", task.getException());
        });
        var thread = new Thread(task, "virtualdiapo-import");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusy(boolean busy) {
        collectionList.setDisable(busy);
        title.setDisable(busy);
        description.setDisable(busy);
        year.setDisable(busy);
        imageList.setDisable(busy);
        saveButton.setDisable(busy);
        resetButton.setDisable(busy);
        chooseButton.setDisable(busy);
        newButton.setDisable(busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
        if (busy) {
            upButton.setDisable(true);
            downButton.setDisable(true);
            removeButton.setDisable(true);
            deleteButton.setDisable(true);
        } else {
            deleteButton.setDisable(editedCollection == null);
            updateImageActions();
        }
    }

    private void updateImageCount() {
        imageCount.setText(selectedImages.size() + (selectedImages.size() > 1 ? " images" : " image"));
    }

    private void updateImageActions() {
        int index = imageList.getSelectionModel().getSelectedIndex();
        upButton.setDisable(index <= 0);
        downButton.setDisable(index < 0 || index >= selectedImages.size() - 1);
        removeButton.setDisable(index < 0);
    }

    private void showOperationError(String header, Throwable failure) {
        var message = errorMessage(failure);
        status.setText(message);
        var error = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        error.setHeaderText(header);
        error.initOwner(collectionList.getScene().getWindow());
        error.initModality(Modality.WINDOW_MODAL);
        error.showAndWait();
    }

    private static String errorMessage(Throwable failure) {
        var current = failure;
        while (current != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        return current == null ? "Une erreur inattendue s’est produite." : current.getMessage();
    }

    private void clearForm() {
        editedCollection = null;
        editorHeading.setText("Nouveau carrousel");
        editorMetadata.setText("Préparez une nouvelle collection de diapositives");
        deleteButton.setDisable(true);
        collectionList.getSelectionModel().clearSelection();
        title.clear();
        description.clear();
        year.clear();
        selectedImages.clear();
        preview.setImage(null);
    }

    private void edit(SlideCollection collection) {
        editedCollection = collection;
        editorHeading.setText(collection.title());
        editorMetadata.setText(collection.slides().size()
                + (collection.slides().size() > 1 ? " diapositives" : " diapositive")
                + (collection.year() == null ? "" : "  ·  " + collection.year()));
        deleteButton.setDisable(false);
        title.setText(collection.title());
        description.setText(collection.description() == null ? "" : collection.description());
        year.setText(collection.year() == null ? "" : collection.year().toString());
        selectedImages.setAll(collection.slides().stream()
                .map(slide -> importer.storedImagePath(slide.id()))
                .toList());
        if (selectedImages.isEmpty()) {
            preview.setImage(null);
        } else {
            imageList.getSelectionModel().selectFirst();
        }
        status.setText("Modification de « " + collection.title() + " » (" + collection.slides().size() + " images).");
    }

    private void deleteCollection() {
        if (editedCollection == null) {
            status.setText("Sélectionnez d’abord une collection.");
            return;
        }
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Supprimer « " + editedCollection.title() + " » et toutes ses images ?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setHeaderText("Suppression définitive");
        confirmation.initOwner(collectionList.getScene().getWindow());
        confirmation.initModality(Modality.WINDOW_MODAL);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            var deletedTitle = editedCollection.title();
            if (!management.delete(editedCollection.id())) {
                throw new IllegalStateException("La collection n’existe plus dans la base de données.");
            }
            clearForm();
            refreshCollections();
            status.setText("Collection « " + deletedTitle + " » supprimée.");
        } catch (Exception exception) {
            showOperationError("La suppression a échoué", exception);
        }
    }

    private void refreshCollections() {
        refreshCollections(null);
    }

    private void refreshCollections(UUID collectionToSelect) {
        collections.setAll(catalog.findAll());
        if (collectionToSelect != null) {
            collections.stream()
                    .filter(collection -> Objects.equals(collection.id(), collectionToSelect))
                    .findFirst()
                    .ifPresent(collectionList.getSelectionModel()::select);
        } else if (editedCollection == null && !collections.isEmpty()) {
            collectionList.getSelectionModel().selectFirst();
        }
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
        Platform.exit();
    }
}
