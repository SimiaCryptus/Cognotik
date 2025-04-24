# Cognotik UI

Cognotik UI is a React-based chat application interface with real-time messaging support via WebSocket. It features theming, modal dialogs, message rendering with Markdown and syntax highlighting, and a robust state management system using Redux Toolkit.

---

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Development](#development)
- [Testing](#testing)
- [Configuration](#configuration)
- [Theming](#theming)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [License](#license)

---

## Features

- Real-time chat interface with WebSocket connectivity.
- Supports multiple themes with dynamic switching.
- Modal dialogs for settings, sessions, and other UI elements.
- Syntax highlighting for code blocks using Prism.js.
- Markdown support with live preview.
- Robust error handling with React Error Boundaries.
- Tab system with state persistence.
- Accessibility and keyboard shortcuts.
- Archive mode for viewing saved chat logs.
- Detailed logging and debugging support.

---

## Installation

### Prerequisites

- Node.js (>= 16.x recommended)
- npm or yarn

### Steps

1. Clone the repository:

   ```bash
   git clone https://github.com/your-org/cognotik-ui.git
   cd cognotik-ui
   ```

2. Install dependencies:

   ```bash
   npm install
   # or
   yarn install
   ```

3. (Optional) Convert SVG logo to PNG:

   ```bash
   node scripts/convertLogo.js
   ```

---

## Usage

### Development Server

Start the development server with hot reloading:

```bash
npm start
# or
yarn start
```

Open your browser and navigate to `http://localhost:3000`.

### Production Build

Build the optimized production bundle:

```bash
npm run build
# or
yarn build
```

Serve the contents of the `build` folder with your preferred static server.

---

## Development

### Code Structure

- `src/`
  - `components/`: React components including `ChatInterface`, `MessageList`, `InputArea`, `Menu`, `Modal`, etc.
  - `hooks/`: Custom React hooks like `useWebSocket`, `useTheme`, and `useModal`.
  - `store/`: Redux Toolkit slices and store configuration.
  - `services/`: WebSocket service and app configuration utilities.
  - `themes/`: Theme definitions and ThemeProvider.
  - `utils/`: Utility functions for logging, UI handlers, and tab management.
  - `styles/`: Global styles and Prism.js theme overrides.
  - `types/`: TypeScript type definitions.
  - `App.tsx`: Main application component.
  - `index.tsx`: Entry point.

### Key Libraries

- React 19
- Redux Toolkit
- styled-components
- Prism.js (syntax highlighting)
- mermaid (diagram rendering)
- react-markdown + remark-gfm (Markdown rendering)
- dompurify (HTML sanitization)
- qrcode-generator (QR code generation)

---

## Testing

Run unit and integration tests:

```bash
npm test
# or
yarn test
```

Tests use React Testing Library and Jest. Custom console logging is configured for better test output.

---

## Configuration

### Environment Variables

- `REACT_APP_API_URL`: Base URL for API requests (optional).

### WebSocket Configuration

- WebSocket settings can be configured via the WebSocket menu in development mode.
- Default WebSocket URL and port are derived from the current window location.
- Supports automatic reconnection with exponential backoff.

### Theme Storage

- Selected theme is persisted in `localStorage` under the key `theme`.
- Verbose mode and other UI preferences are also persisted.

---

## Theming

- Multiple themes are available: `default`, `main`, `night`, `forest`, `pony`, `alien`, `sunset`, `ocean`, `cyberpunk`.
- Themes control colors, typography, shadows, and transitions.
- Theme switching is dynamic and updates CSS variables and Prism.js styles.
- Keyboard shortcut support for theme modal (`Alt+T` or `Ctrl+T`).

---

## Architecture

- **State Management:** Redux Toolkit slices for UI, user, messages, connection, and configuration.
- **WebSocket Service:** Singleton service managing connection, message queue, heartbeat, and reconnection logic.
- **UI Components:** Modular React components with styled-components for styling.
- **Error Handling:** React Error Boundary with fallback UI and structured logging.
- **Performance:** Debounced and batched updates for tabs, messages, and syntax highlighting.
- **Accessibility:** Keyboard navigation, ARIA attributes, and focus management.

---

## Contributing

Contributions are welcome! Please open issues or pull requests on the GitHub repository.

---

## License

This project is licensed under the MIT License.

---

## Acknowledgments

- [Prism.js](https://prismjs.com/)
- [Mermaid](https://mermaid-js.github.io/)
- [Redux Toolkit](https://redux-toolkit.js.org/)
- [React](https://reactjs.org/)
- [Styled Components](https://styled-components.com/)

---

For any questions or support, please contact the maintainers.