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
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;

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
    private final Button deleteButton = new Button("Supprimer");

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
        stage.setScene(new Scene(buildRoot(stage), 1180, 760));
        stage.show();
        refreshCollections();
    }

    private BorderPane buildRoot(Stage stage) {
        var root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(new SplitPane(buildCatalogPane(), buildImportPane(stage)));
        return root;
    }

    private VBox buildHeader() {
        var heading = new Label("VIRTUALDIAPO");
        heading.setStyle("-fx-font-size: 25px; -fx-font-weight: bold;");
        var subtitle = new Label("Le serveur est actif sur le port 8080");
        var box = new VBox(3, heading, subtitle);
        box.setPadding(new Insets(18, 24, 14, 24));
        return box;
    }

    private VBox buildCatalogPane() {
        var heading = new Label("Collections disponibles");
        heading.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        collectionList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(SlideCollection item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title() + "  ·  " + item.slides().size() + " images");
            }
        });
        collectionList.getSelectionModel().selectedItemProperty().addListener((ignored, oldValue, selected) -> {
            if (selected != null) {
                edit(selected);
            }
        });
        VBox.setVgrow(collectionList, Priority.ALWAYS);
        var pane = new VBox(12, heading, collectionList);
        pane.setPadding(new Insets(18));
        pane.setMinWidth(300);
        return pane;
    }

    private VBox buildImportPane(Stage stage) {
        var heading = new Label("Nouvelle collection");
        heading.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        title.setPromptText("Titre");
        description.setPromptText("Description (facultative)");
        description.setPrefRowCount(2);
        year.setPromptText("Année (facultative)");

        preview.setFitWidth(320);
        preview.setFitHeight(210);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        var previewBox = new BorderPane(preview);
        previewBox.setMinHeight(220);
        previewBox.setStyle("-fx-background-color: #171512;");
        BorderPane.setAlignment(preview, Pos.CENTER);

        imageList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (getIndex() + 1) + ".  " + item.getFileName());
            }
        });
        imageList.getSelectionModel().selectedItemProperty().addListener((ignored, oldValue, path) -> showPreview(path));
        VBox.setVgrow(imageList, Priority.ALWAYS);

        var choose = new Button("Ajouter des JPEG…");
        choose.setOnAction(event -> chooseImages(stage));
        var up = new Button("Monter");
        up.setOnAction(event -> moveSelected(-1));
        var down = new Button("Descendre");
        down.setOnAction(event -> moveSelected(1));
        var remove = new Button("Retirer");
        remove.setOnAction(event -> removeSelected());
        var imageActions = new HBox(8, choose, up, down, remove);

        var create = new Button("Enregistrer");
        create.setDefaultButton(true);
        create.setOnAction(event -> saveCollection(create));
        var reset = new Button("Nouvelle");
        reset.setOnAction(event -> clearForm());
        deleteButton.setDisable(true);
        deleteButton.setOnAction(event -> deleteCollection());
        status.setWrapText(true);
        var footer = new HBox(10, create, reset, deleteButton, status);
        footer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(status, Priority.ALWAYS);

        var pane = new VBox(10, heading, title, description, year, new Separator(Orientation.HORIZONTAL),
                imageActions, imageList, previewBox, footer);
        pane.setPadding(new Insets(18));
        pane.setMinWidth(590);
        return pane;
    }

    private void chooseImages(Stage stage) {
        var chooser = new FileChooser();
        chooser.setTitle("Choisir les diapositives JPEG");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images JPEG", "*.jpg", "*.jpeg", "*.JPG", "*.JPEG"));
        var files = chooser.showOpenMultipleDialog(stage);
        if (files != null) {
            selectedImages.addAll(files.stream().map(java.io.File::toPath).toList());
            imageList.getSelectionModel().select(selectedImages.size() - files.size());
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

    private void saveCollection(Button createButton) {
        final Integer parsedYear;
        try {
            parsedYear = year.getText().isBlank() ? null : Integer.valueOf(year.getText().trim());
        } catch (NumberFormatException exception) {
            status.setText("L’année doit être un nombre.");
            return;
        }
        var files = List.copyOf(selectedImages);
        var task = new Task<SlideCollection>() {
            @Override
            protected SlideCollection call() throws Exception {
                if (editedCollection != null) {
                    return management.update(editedCollection.id(), title.getText(), description.getText(), parsedYear);
                }
                return importer.importFiles(title.getText(), description.getText(), parsedYear, files);
            }
        };
        createButton.setDisable(true);
        status.setText("Import en cours…");
        task.setOnSucceeded(event -> {
            createButton.setDisable(false);
            status.setText("Collection « " + task.getValue().title() + " » enregistrée.");
            clearForm();
            refreshCollections();
        });
        task.setOnFailed(event -> {
            createButton.setDisable(false);
            status.setText(task.getException().getMessage());
        });
        var thread = new Thread(task, "virtualdiapo-import");
        thread.setDaemon(true);
        thread.start();
    }

    private void clearForm() {
        editedCollection = null;
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
            status.setText(exception.getMessage());
            var error = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
            error.setHeaderText("La suppression a échoué");
            error.initOwner(collectionList.getScene().getWindow());
            error.showAndWait();
        }
    }

    private void refreshCollections() {
        collections.setAll(catalog.findAll());
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
        Platform.exit();
    }
}
