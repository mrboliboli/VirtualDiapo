package fr.virtualdiapo.desktop.network;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public final class MdnsPublisher implements SmartLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(MdnsPublisher.class);
    public static final String SERVICE_TYPE = "_virtualdiapo._tcp.local.";
    private final Object lock = new Object();
    private final Map<InetAddress, JmDNS> publishers = new HashMap<>();
    private ScheduledExecutorService monitor;
    private volatile boolean running;

    @Override
    public void start() {
        ScheduledExecutorService newMonitor;
        synchronized (lock) {
            if (monitor != null) return;
            newMonitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                var thread = new Thread(runnable, "virtualdiapo-mdns-monitor");
                thread.setDaemon(true);
                return thread;
            });
            monitor = newMonitor;
            try {
                newMonitor.scheduleWithFixedDelay(this::refreshPublishers, 5, 5, TimeUnit.SECONDS);
            } catch (RejectedExecutionException exception) {
                monitor = null;
                newMonitor.shutdownNow();
                throw exception;
            }
        }
        refreshPublishers();
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (monitor != null) {
                monitor.shutdownNow();
                monitor = null;
            }
            publishers.values().forEach(MdnsPublisher::closeQuietly);
            publishers.clear();
            running = false;
        }
    }

    @Override public boolean isRunning() { return running; }

    private void refreshPublishers() {
        try {
            var addresses = eligibleAddresses();
            synchronized (lock) {
                if (monitor == null) return;
                publishers.keySet().removeIf(address -> {
                    if (addresses.contains(address)) return false;
                    closeQuietly(publishers.get(address));
                    LOGGER.info("Publication mDNS arrêtée sur {}", address.getHostAddress());
                    return true;
                });
                for (var address : addresses) {
                    if (publishers.containsKey(address)) continue;
                    try {
                        var publisher = JmDNS.create(address);
                        publisher.registerService(ServiceInfo.create(SERVICE_TYPE, "VirtualDiapo", 8080, 0, 0,
                                Map.of("apiVersion", "1")));
                        publishers.put(address, publisher);
                        LOGGER.info("Publication mDNS active sur {}", address.getHostAddress());
                    } catch (IOException exception) {
                        LOGGER.warn("Publication mDNS impossible sur {}", address.getHostAddress(), exception);
                    }
                }
                running = !publishers.isEmpty();
                if (!running) LOGGER.warn("Publication mDNS indisponible, la connexion manuelle reste active");
            }
        } catch (RuntimeException | SocketException exception) {
            LOGGER.warn("Impossible d'actualiser les interfaces mDNS", exception);
        }
    }

    static Set<InetAddress> eligibleAddresses() throws SocketException {
        var addresses = new LinkedHashSet<InetAddress>();
        var interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return addresses;
        for (var networkInterface : Collections.list(interfaces)) {
            if (!networkInterface.isUp() || networkInterface.isLoopback() || !networkInterface.supportsMulticast()) continue;
            for (var address : Collections.list(networkInterface.getInetAddresses())) {
                if (address.isLoopbackAddress() || address.isAnyLocalAddress()) continue;
                if (address instanceof Inet4Address
                        || address instanceof Inet6Address && !address.isLinkLocalAddress()) {
                    addresses.add(address);
                }
            }
        }
        return addresses;
    }

    private static void closeQuietly(JmDNS publisher) {
        try {
            publisher.close();
        } catch (IOException exception) {
            LOGGER.debug("Erreur à la fermeture d'une publication mDNS", exception);
        }
    }
}
