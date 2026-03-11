package com.infocaption.dashboard.util;

import java.io.IOException;

/**
 * Interface for upstream MCP server connections.
 * Implementations handle either HTTP or stdio transport.
 */
public interface McpConnection {

    /**
     * Send a JSON-RPC request and return the raw JSON response.
     */
    String sendRequest(String jsonRpcRequest) throws IOException;

    /**
     * Fetch the tools/list from the upstream server.
     * Returns raw JSON response body.
     */
    String listTools() throws IOException;

    /**
     * Check if the connection is still alive.
     */
    boolean isAlive();

    /**
     * Close the connection and release resources.
     */
    void close();

    /**
     * Get the transport type identifier.
     */
    String getTransportType();
}
