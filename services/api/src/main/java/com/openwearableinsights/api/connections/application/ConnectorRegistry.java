package com.openwearableinsights.api.connections.application;

import com.openwearableinsights.api.connections.domain.WearableConnector;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Dispatches to whichever registered {@link WearableConnector} can handle a
 * given file. Spring autowires every WearableConnector bean into the list —
 * adding a new wearable connector never requires touching this class.
 */
@Service
public class ConnectorRegistry {

    private final List<WearableConnector> connectors;

    public ConnectorRegistry(List<WearableConnector> connectors) {
        this.connectors = connectors;
    }

    public Optional<WearableConnector> findFor(String filename) {
        return connectors.stream().filter(c -> c.supports(filename)).findFirst();
    }
}
