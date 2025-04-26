# Chat Web Application

A modern, feature-rich chat web application built with React, TypeScript, and WebSocket communication.

## Features

### Core Functionality
- Real-time chat messaging using WebSocket connections
- Support for HTML and Markdown message formatting
- Message history and threading
- Reference message linking
- Code syntax highlighting with Prism.js
- Mermaid diagram support
- QR code generation

### UI/UX
- Multiple theme support with smooth transitions
- Responsive design with mobile optimization
- Accessible components following ARIA guidelines
- Rich text editor with Markdown toolbar
- Message preview mode
- Loading states and error handling
- Archive mode for offline viewing

### Themes
- 8 built-in themes:
  - Main (Light)
  - Night (Dark)
  - Forest
  - Pony
  - Alien
  - Sunset
  - Ocean
  - Cyberpunk
- Theme persistence in localStorage
- Auto theme switching support

### Technical Features
- TypeScript for type safety
- Redux state management with slices
- Styled Components for CSS-in-JS
- WebSocket connection management with auto-reconnect
- Message queuing and batching
- Performance optimizations
- Comprehensive error handling
- Detailed logging system
- Unit test setup

## Project Structure

```
webapp/
├── src/
│   ├── components/      # React components
│   ├── hooks/          # Custom React hooks
│   ├── services/       # Core services (WebSocket, config)
│   ├── store/          # Redux store and slices
│   ├── styles/         # Global styles
│   ├── themes/         # Theme definitions
│   ├── types/          # TypeScript type definitions
│   └── utils/          # Utility functions
```

## Key Components

### Chat Interface
- `ChatInterface.tsx`: Main chat container component
- `MessageList.tsx`: Renders chat messages with formatting
- `InputArea.tsx`: Message input with Markdown editor

### Theme System
- `ThemeProvider.tsx`: Theme context and switching
- `ThemeMenu.tsx`: Theme selection UI
- `themes.ts`: Theme definitions and utilities

### State Management
- Redux store with slices for:
  - Messages
  - UI state
  - Configuration
  - User data

## Setup & Development

### Prerequisites
- Node.js 14+
- npm or yarn

### Installation
```bash
# Install dependencies
npm install

# Start development server
npm start

# Run tests
npm test

# Build for production
npm run build
```

### Environment Variables
- `REACT_APP_API_URL`: Backend API URL (optional)
- `NODE_ENV`: Environment mode ('development' or 'production')

## WebSocket Communication

The app uses a WebSocket connection for real-time messaging with features like:

- Automatic reconnection with exponential backoff
- Message queuing
- Connection state management
- Error handling
- Message batching for performance

## Styling

The project uses Styled Components with:

- Global styles
- Theme-based styling
- CSS variables
- Responsive design
- Smooth transitions
- Accessibility features

## Testing

- Jest test setup
- React Testing Library
- Console output formatting
- Error boundary testing

## Browser Support

- Modern browsers (Chrome, Firefox, Safari, Edge)
- Fallback handling for older browsers
- Responsive design for mobile devices

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## Acknowledgments

- React team for the core framework
- Styled Components for the styling system
- Redux team for state management
- PrismJS for code highlighting
- Mermaid for diagrams

## Available Scripts

In the project directory, you can run:

### `npm start`

Runs the app in the development mode.\
Open [http://localhost:3000](http://localhost:3000) to view it in your browser.

The page will reload when you make changes.\
You may also see any lint errors in the console.

### `npm test`

Launches the test runner in the interactive watch mode.\
See the section about [running tests](https://facebook.github.io/create-react-app/docs/running-tests) for more information.

### `npm run build`

Builds the app for production to the `build` folder.\
It correctly bundles React in production mode and optimizes the build for the best performance.

The build is minified and the filenames include the hashes.\
Your app is ready to be deployed!

See the section about [deployment](https://facebook.github.io/create-react-app/docs/deployment) for more information.

### `npm run eject`

**Note: this is a one-way operation. Once you `eject`, you can't go back!**

If you aren't satisfied with the build tool and configuration choices, you can `eject` at any time. This command will remove the single build dependency from
your project.

Instead, it will copy all the configuration files and the transitive dependencies (webpack, Babel, ESLint, etc) right into your project so you have full control
over them. All of the commands except `eject` will still work, but they will point to the copied scripts so you can tweak them. At this point you're on your
own.

You don't have to ever use `eject`. The curated feature set is suitable for small and middle deployments, and you shouldn't feel obligated to use this feature.
However we understand that this tool wouldn't be useful if you couldn't customize it when you are ready for it.

## Learn More

You can learn more in the [Create React App documentation](https://facebook.github.io/create-react-app/docs/getting-started).

To learn React, check out the [React documentation](https://reactjs.org/).

### Code Splitting

This section has moved here: [https://facebook.github.io/create-react-app/docs/code-splitting](https://facebook.github.io/create-react-app/docs/code-splitting)

### Analyzing the Bundle Size

This section has moved
here: [https://facebook.github.io/create-react-app/docs/analyzing-the-bundle-size](https://facebook.github.io/create-react-app/docs/analyzing-the-bundle-size)

### Making a Progressive Web App

This section has moved
here: [https://facebook.github.io/create-react-app/docs/making-a-progressive-web-app](https://facebook.github.io/create-react-app/docs/making-a-progressive-web-app)

### Advanced Configuration

This section has moved
here: [https://facebook.github.io/create-react-app/docs/advanced-configuration](https://facebook.github.io/create-react-app/docs/advanced-configuration)

### Deployment

This section has moved here: [https://facebook.github.io/create-react-app/docs/deployment](https://facebook.github.io/create-react-app/docs/deployment)

### `npm run build` fails to minify

This section has moved
here: [https://facebook.github.io/create-react-app/docs/troubleshooting#npm-run-build-fails-to-minify](https://facebook.github.io/create-react-app/docs/troubleshooting#npm-run-build-fails-to-minify)