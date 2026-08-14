package fr.virtualdiapo.desktop.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/server")
public final class ServerController {
    @GetMapping
    public ServerResponse serverInformation() {
        return new ServerResponse("VirtualDiapo", 1);
    }

    public record ServerResponse(String name, int apiVersion) {}
}

