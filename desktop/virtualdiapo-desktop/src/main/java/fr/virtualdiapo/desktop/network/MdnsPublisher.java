package fr.virtualdiapo.desktop.network;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

@Component
public final class MdnsPublisher implements SmartLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(MdnsPublisher.class);
    public static final String SERVICE_TYPE = "_virtualdiapo._tcp.local.";
    private JmDNS jmdns;
    private boolean running;

    @Override
    public void start() {
        try {
            jmdns = JmDNS.create();
            jmdns.registerService(ServiceInfo.create(SERVICE_TYPE, "VirtualDiapo", 8080, 0, 0,
                    Map.of("apiVersion", "1")));
            running = true;
        } catch (IOException exception) {
            LOGGER.warn("Publication mDNS indisponible, la connexion manuelle reste active", exception);
            running = false;
        }
    }

    @Override
    public void stop() {
        if (jmdns != null) {
            try { jmdns.close(); } catch (IOException ignored) { }
        }
        running = false;
    }

    @Override public boolean isRunning() { return running; }
}
