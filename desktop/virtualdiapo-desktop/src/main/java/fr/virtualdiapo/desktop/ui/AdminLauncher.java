package fr.virtualdiapo.desktop.ui;

import javafx.application.Application;

public final class AdminLauncher {
    private AdminLauncher() {}

    public static void main(String[] args) {
        Application.launch(AdminWindow.class, args);
    }
}
