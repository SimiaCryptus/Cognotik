# Illustration Spec: Component Interaction Diagram

## Topic
Illustrate the runtime interaction between the major components of the Cognotik
platform, from the user's browser down through the platform services.

## Format
- Rendered as a layered box-and-arrow diagram (top-to-bottom flow).
- Aspect ratio: 4:3 (suitable for embedding in documentation and slides).
- Color-coded subsystem groupings with a legend.

## Layout & Content

### Top: Entry Point
- A single node labeled **"User (Browser)"** at the top center.
- Two arrows leave the user:
- A one-directional arrow to "Jetty Servlets" (HTTP requests).
- A bi-directional arrow to "WebSocket / SocketManager" (live UI channel).

### Grouped Layers (each drawn as a labeled, color-filled container box)

1. **Web / Transport Layer** (fill: #4A90E2, white text)
- `Jetty Servlets`
- `WebSocket / SocketManager`
- `SessionTask (UI)`

2. **Application Layer** (fill: default/light)
- `ApplicationServer / SingleTaskApp`
- `SessionProxyServer`

3. **Cognitive Planning Layer** (fill: #7ED321, black text)
- `CognitiveMode`
- `Orchestrator`
- `TaskType Registry`

4. **Agent Layer** (fill: #F5A623, black text)
- `Agents (Chat/Parsed/Code/Proxy)`
- `TypeDescriber`

5. **Model & Provider Layer** (fill: #BD10E0, white text)
- `APIProvider`
- `ChatModel / EmbeddingModel / ImageModel`

6. **Platform Layer** (fill: #50E3C2, black text)
- `Storage / Metadata`
- `Auth Manager`
- `Thread Pool Manager`
- `User Settings`

## Key Directed Edges
- User → Servlets; User ↔ WS
- Servlets → App; WS → App
- App → Proxy; App → Mode
- Mode → Orchestrator → TaskType Registry → Agents
- Agents → TypeDescriber; Agents → Models → Providers
- SessionTask → WS (server-driven UI push)
- App → Storage; App → Auth; App → Pools
- Agents → User Settings

## Emphasis / Callouts
- Highlight the descending dependency direction (higher layers depend on lower).
- Annotate the WebSocket edge with "Server-Driven UI".
- Annotate the Agents→Models edge with "Strongly-typed I/O".

## Reference
Corresponds to Section 2 ("Component Interaction") of diagrams.md and the
Component Interaction mermaid graph in architecture_overview.md.