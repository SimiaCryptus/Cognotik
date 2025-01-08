# SkyeNet Frontend Documentation

## Overview

The SkyeNet frontend is a React-based application that provides a real-time chat interface with advanced features like theming, WebSocket communication, and modular component architecture.

## Component Hierarchy

### Core Components

1. **ChatInterface**
   - Main container component for the chat application
   - Manages WebSocket connection and message handling
   - Contains MessageList and InputArea components

2. **MessageList**
   - Displays chat messages in chronological order
   - Handles message formatting and HTML sanitization
   - Supports code syntax highlighting and markdown rendering

3. **InputArea**
   - Provides rich text input with markdown support
   - Implements toolbar for formatting options
   - Handles message submission and validation

4. **Menu System**
   - ThemeMenu: Theme selection and management
   - WebSocketMenu: Connection configuration (dev mode)
   - Navigation and session management

### Error Handling

1. **ErrorBoundary**
   - Catches and handles React component errors
   - Provides fallback UI for error states
   - Logs errors for debugging

2. **ErrorFallback**
   - Displays user-friendly error messages
   - Maintains app stability during errors

## State Management

### Redux Store Structure

```typescript
interface RootState {
  ui: {
    theme: ThemeName;
    modalOpen: boolean;
    modalType: string | null;
    modalContent: string;
    verboseMode: boolean;
  };
  config: {
    websocket: WebSocketConfig;
    singleInput: boolean;
    showMenubar: boolean;
    // ... other config options
  };
  messages: {
    messages: Message[];
    pendingMessages: Message[];
    messageQueue: Message[];
    isProcessing: boolean;
  };
  user: {
    name: string;
    isAuthenticated: boolean;
    preferences: Record<string, unknown>;
  };
}
```

### State Slices

1. **UI Slice**
   - Theme management
   - Modal control
   - Verbose mode toggle
   - Layout preferences

2. **Config Slice**
   - WebSocket configuration
   - Application settings
   - Feature flags

3. **Message Slice**
   - Message history
   - Message queue management
   - Processing state

4. **User Slice**
   - Authentication state
   - User preferences
   - Session management

## WebSocket Communication

### Connection Management

```typescript
class WebSocketService {
  // Connection states
  private connectionState: 'connecting' | 'connected' | 'disconnected' | 'error';
  private reconnectAttempts: number;
  
  // Heartbeat configuration
  private readonly HEARTBEAT_INTERVAL = 30000;
  private readonly HEARTBEAT_TIMEOUT = 5000;
  
  // Message handling
  public addMessageHandler(handler: (data: Message) => void): void;
  public removeMessageHandler(handler: (data: Message) => void): void;
  
  // Connection methods
  public connect(config: WebSocketConfig): void;
  public disconnect(): void;
  public reconnect(): void;
}
```

### Message Protocol

1. **Message Format**
```typescript
interface Message {
  id: string;
  content: string;
  type: MessageType;
  version: number;
  timestamp: number;
  isHtml: boolean;
  rawHtml: string | null;
  sanitized: boolean;
}
```

2. **Message Types**
- user: User input messages
- assistant: AI responses
- system: System notifications
- error: Error messages
- loading: Loading states
- reference: Referenced content

## Theming System

### Theme Structure

```typescript
interface Theme {
  colors: {
    primary: string;
    secondary: string;
    background: string;
    surface: string;
    text: {
      primary: string;
      secondary: string;
    };
    border: string;
    // ... other colors
  };
  typography: {
    fontFamily: string;
    fontSize: {
      xs: string;
      sm: string;
      md: string;
      lg: string;
      xl: string;
    };
    // ... other typography settings
  };
  sizing: {
    spacing: {
      xs: string;
      sm: string;
      md: string;
      lg: string;
      xl: string;
    };
    borderRadius: {
      sm: string;
      md: string;
      lg: string;
    };
  };
}
```

### Available Themes

1. Main (Light)
2. Night (Dark)
3. Forest
4. Pony
5. Alien
6. Sunset
7. Ocean
8. Cyberpunk

### Theme Management

```typescript
const ThemeProvider: React.FC = ({ children }) => {
  // Theme state management
  const currentTheme = useSelector((state: RootState) => state.ui.theme);
  
  // Theme switching
  useEffect(() => {
    // Apply theme CSS variables
    // Update Prism.js theme
    // Handle transitions
  }, [currentTheme]);
  
  return (
    <StyledThemeProvider theme={themes[currentTheme]}>
      <GlobalStyles />
      {children}
    </StyledThemeProvider>
  );
};
```

## User Interface Features

### Rich Text Input

1. **Markdown Support**
   - Syntax highlighting
   - Live preview
   - Custom toolbar

2. **Code Formatting**
   - Multiple language support
   - Syntax highlighting
   - Copy functionality

### Message Display

1. **Message Types**
   - User messages
   - AI responses
   - System notifications
   - Error messages

2. **Content Rendering**
   - HTML sanitization
   - Markdown rendering
   - Code block formatting

### Interactive Elements

1. **Buttons**
   - Message actions
   - Theme switching
   - Connection management

2. **Modals**
   - Settings
   - File management
   - Error displays

## Best Practices

### Performance Optimization

1. **State Management**
   - Memoized selectors
   - Debounced updates
   - Optimized renders

2. **Resource Loading**
   - Lazy loading
   - Code splitting
   - Asset optimization

### Security

1. **Input Sanitization**
   - HTML sanitization
   - XSS prevention
   - Content validation

2. **WebSocket Security**
   - Secure connection handling
   - Message validation
   - Error handling

### Accessibility

1. **ARIA Support**
   - Proper labeling
   - Keyboard navigation
   - Screen reader support

2. **Visual Accessibility**
   - Color contrast
   - Font scaling
   - Focus indicators

## Development Guidelines

### Code Organization

1. **Component Structure**
   - Functional components
   - Custom hooks
   - Utility functions

2. **State Management**
   - Redux actions
   - Selector patterns
   - Side effect handling

### Testing

1. **Component Testing**
   - Unit tests
   - Integration tests
   - Snapshot testing

2. **State Testing**
   - Redux store tests
   - Action creators
   - Reducer logic

### Styling

1. **Styled Components**
   - Theme integration
   - Component-specific styles
   - Global styles

2. **CSS Organization**
   - BEM methodology
   - CSS-in-JS patterns
   - Responsive design