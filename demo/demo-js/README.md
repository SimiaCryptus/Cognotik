# Cognotik Demo - JavaScript Testing Suite

A comprehensive testing framework for the Cognotik AI assistant interface, featuring unit tests, end-to-end tests, and
cross-browser compatibility testing.

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Project Structure](#project-structure)
- [Testing Framework](#testing-framework)
- [Running Tests](#running-tests)
- [Test Categories](#test-categories)
- [Configuration](#configuration)
- [Development Workflow](#development-workflow)
- [CI/CD Integration](#cicd-integration)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## 🎯 Overview

This testing suite provides comprehensive coverage for the Cognotik welcome interface, ensuring reliability,
accessibility, and cross-browser compatibility. The suite includes both unit tests using Jest and end-to-end tests using
Cypress.

### Key Components Tested

- **User Settings Management**: API key configuration, local tools setup
- **Cognitive Mode Selection**: Auto-plan vs basic chat workflows
- **Task Configuration**: Model selection, parameter tuning, task enablement
- **Complete Workflows**: End-to-end user journeys
- **Error Handling**: Validation, network errors, edge cases
- **Accessibility**: ARIA compliance, keyboard navigation
- **Responsive Design**: Multi-device compatibility

## ✨ Features

### Testing Capabilities

- **Unit Testing**: Component-level testing with Jest
- **E2E Testing**: Full workflow testing with Cypress
- **Cross-Browser Testing**: Chrome, Firefox, Edge compatibility
- **Responsive Testing**: Mobile, tablet, desktop viewports
- **Accessibility Testing**: WCAG compliance validation
- **Performance Testing**: Load time and efficiency metrics
- **Error Handling**: Comprehensive error scenario coverage

### Development Tools

- **Page Object Model**: Maintainable test structure
- **Custom Commands**: Reusable test utilities
- **Mock Data**: Fixtures for consistent testing
- **Visual Testing**: Screenshot comparison
- **Code Coverage**: Detailed coverage reports

## 🔧 Prerequisites

### System Requirements

- **Node.js**: Version 16.0 or higher
- **npm**: Version 8.0 or higher
- **Git**: For version control

### Browser Requirements

- **Chrome**: Version 90+ (recommended for development)
- **Firefox**: Version 88+
- **Edge**: Version 90+

### Development Environment

```bash
# Check Node.js version
node --version  # Should be 16.0+

# Check npm version
npm --version   # Should be 8.0+
```

## 📦 Installation

### 1. Clone the Repository

```bash
git clone <repository-url>
cd demo/demo-js
```

### 2. Install Dependencies

```bash
# Install all dependencies
npm install

# Install development dependencies
npm install --save-dev jest @testing-library/jest-dom cypress eslint
```

### 3. Install Cypress (if not included)

```bash
# Install Cypress globally (optional)
npm install -g cypress

# Or use npx for local installation
npx cypress install
```

### 4. Verify Installation

```bash
# Run a quick test to verify setup
npm run test:unit -- --passWithNoTests
npm run test:e2e:open  # Opens Cypress GUI
```

## 📁 Project Structure

```
demo/demo-js/
├── cypress/                    # Cypress E2E tests
│   ├── e2e/                   # Test specifications
│   │   ├── accessibility.cy.js
│   │   ├── complete-workflow.cy.js
│   │   ├── cross-browser.cy.js
│   │   ├── error-handling.cy.js
│   │   ├── performance.cy.js
│   │   ├── responsive.cy.js
│   │   └── user-settings.cy.js
│   ├── fixtures/              # Test data
│   │   ├── api-settings.json
│   │   ├── models.json
│   │   ├── task-settings.json
│   │   └── user-settings.json
│   └── support/               # Support files
│       ├── commands.js        # Custom commands
│       ├── e2e.js            # Global configuration
│       └── page-objects/      # Page object models
│           ├── WelcomePage.js
│           └── UserSettingsModal.js
├── tests/                     # Unit tests
│   └── setup.js              # Jest configuration
├── cypress.config.js          # Cypress configuration
├── package.json              # Dependencies and scripts
├── .eslintrc.js              # ESLint configuration
└── README.md                 # This file
```

## 🧪 Testing Framework

### Unit Testing (Jest)

- **Framework**: Jest with Testing Library
- **Purpose**: Component logic and utility function testing
- **Coverage**: DOM manipulation, data validation, state management

### End-to-End Testing (Cypress)

- **Framework**: Cypress
- **Purpose**: Full user workflow testing
- **Coverage**: User interactions, API integration, navigation flows

### Page Object Model

```javascript
// Example usage
import { WelcomePage, UserSettingsModal } from '../support/page-objects';

const welcomePage = new WelcomePage();
const userSettings = new UserSettingsModal();

welcomePage
  .visit()
  .openUserSettings();

userSettings
  .setApiKey('OpenAI', 'test-key')
  .save();
```

## 🚀 Running Tests

### Quick Start

```bash
# Run all tests
npm test

# Run only unit tests
npm run test:unit

# Run only E2E tests
npm run test:e2e

# Open Cypress GUI for interactive testing
npm run test:e2e:open
```

### Detailed Test Commands

#### Unit Tests

```bash
# Run unit tests with coverage
npm run test:coverage

# Run unit tests in watch mode
npm run test:unit -- --watch

# Run specific test file
npm run test:unit -- tests/specific-test.js
```

#### End-to-End Tests

```bash
# Run E2E tests headlessly
npm run test:e2e:headless

# Run E2E tests in specific browser
npm run test:e2e:chrome
npm run test:e2e:firefox

# Run specific test file
npx cypress run --spec "cypress/e2e/user-settings.cy.js"

# Run tests with specific viewport
npx cypress run --config viewportWidth=375,viewportHeight=667
```

#### Cross-Browser Testing

```bash
# Test in Chrome
npm run test:e2e:chrome

# Test in Firefox
npm run test:e2e:firefox

# Test in multiple browsers (requires CI setup)
npm run test:e2e -- --browser chrome,firefox,edge
```

#### Running a demo for recording

```shell
npx cypress run --browser chrome --spec "cypress/e2e/demos/demo_launcher_introduction.cy.js" --headed --no-runner-ui
```

## 📊 Test Categories

### 1. User Settings Tests (`user-settings.cy.js`)

Tests the user settings modal functionality:

- API key configuration
- Settings persistence
- Validation and error handling
- Modal interactions

```javascript
// Example test
it('should save API keys to localStorage', () => {
  cy.setupApiKeys(['OpenAI', 'Anthropic']);
  cy.window().its('localStorage')
    .invoke('getItem', 'apiKeys')
    .should('contain', 'OpenAI');
});
```

### 2. Complete Workflow Tests (`complete-workflow.cy.js`)

Tests end-to-end user journeys:

- Full configuration workflow
- Auto-plan setup and launch
- Basic chat configuration
- Navigation between steps

### 3. Accessibility Tests (`accessibility.cy.js`)

Ensures WCAG compliance:

- ARIA labels and roles
- Keyboard navigation
- Color contrast
- Screen reader compatibility

### 4. Responsive Design Tests (`responsive.cy.js`)

Tests across different viewports:

- Mobile (375x667)
- Tablet (768x1024)
- Desktop (1280x720)
- Large Desktop (1920x1080)

### 5. Error Handling Tests (`error-handling.cy.js`)

Tests error scenarios:

- Missing API keys
- Network failures
- Invalid configurations
- Server errors

### 6. Performance Tests (`performance.cy.js`)

Measures application performance:

- Page load times
- API response times
- Memory usage
- Rendering performance

### 7. Cross-Browser Tests (`cross-browser.cy.js`)

Ensures compatibility across browsers:

- Chrome compatibility
- Firefox compatibility
- Edge compatibility
- Feature detection

## ⚙️ Configuration

### Cypress Configuration (`cypress.config.js`)

```javascript
module.exports = defineConfig({
  e2e: {
    baseUrl: 'http://localhost:8080',
    viewportWidth: 1280,
    viewportHeight: 720,
    video: true,
    screenshotOnRunFailure: true,
    defaultCommandTimeout: 10000
  }
});
```

### Jest Configuration (in `package.json`)

```json
{
  "jest": {
    "testEnvironment": "jsdom",
    "setupFilesAfterEnv": ["<rootDir>/tests/setup.js"],
    "collectCoverageFrom": [
      "**/*.js",
      "!**/node_modules/**",
      "!**/cypress/**"
    ]
  }
}
```

### ESLint Configuration (`.eslintrc.js`)

```javascript
module.exports = {
  env: {
    browser: true,
    es2021: true,
    'cypress/globals': true
  },
  extends: ['eslint:recommended'],
  plugins: ['cypress']
};
```

## 🔄 Development Workflow

### 1. Writing New Tests

#### Unit Test Example

```javascript
// tests/user-settings.test.js
import { saveUserSettings } from '../src/user-settings.js';

describe('User Settings', () => {
  test('should save API keys to localStorage', () => {
    const settings = { apiKeys: { OpenAI: 'test-key' } };
    saveUserSettings(settings);

    expect(localStorage.setItem).toHaveBeenCalledWith(
      'userSettings',
      JSON.stringify(settings)
    );
  });
});
```

#### E2E Test Example

```javascript
// cypress/e2e/new-feature.cy.js
describe('New Feature', () => {
  beforeEach(() => {
    cy.visit('/welcome');
    cy.clearAppData();
  });

  it('should handle new feature workflow', () => {
    cy.setupApiKeys(['OpenAI']);
    cy.configureTaskSettings({ model: 'GPT4o' });
    cy.selectTasks(['InsightTask']);
    cy.navigateToStep('launch');
    cy.get('#launch-session').click();

    cy.url().should('include', '/autoPlan');
  });
});
```

### 2. Using Custom Commands

```javascript
// Custom command usage
cy.clearAppData();                    // Clear all storage
cy.setupApiKeys(['OpenAI']);          // Setup API keys
cy.configureTaskSettings({            // Configure settings
  model: 'GPT4o',
  temperature: 0.5
});
cy.selectTasks(['InsightTask']);      // Select tasks
cy.mockApiResponses();                // Mock API calls
```

### 3. Using Page Objects

```javascript
import { WelcomePage } from '../support/page-objects';

const page = new WelcomePage();

page
  .visit()
  .selectCognitiveMode('auto-plan')
  .configureAutoPlanSettings(20000, 10)
  .navigateToTaskSettings()
  .configureTaskSettings({ model: 'GPT4o' })
  .navigateToTaskSelection()
  .selectTasks(['InsightTask', 'FileModificationTask'])
  .navigateToLaunch()
  .launch();
```

### 4. Test Data Management

```javascript
// Using fixtures
cy.fixture('user-settings').then((settings) => {
  cy.window().then((win) => {
    win.localStorage.setItem('userSettings', JSON.stringify(settings));
  });
});

// Using custom data
const testData = {
  apiKeys: { OpenAI: 'test-key' },
  taskSettings: { model: 'GPT4o' }
};
```

## 🔧 CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/test.yml
name: Test Suite

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'

    - name: Install dependencies
      run: npm ci

    - name: Run unit tests
      run: npm run test:unit

    - name: Run E2E tests
      uses: cypress-io/github-action@v5
      with:
        start: npm start
        wait-on: 'http://localhost:8080'
        browser: chrome

    - name: Upload coverage
      uses: codecov/codecov-action@v3
```

### Docker Integration

```dockerfile
# Dockerfile.test
FROM cypress/browsers:node18.12.0-chrome107-ff107

WORKDIR /app
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run test:unit
RUN npm run test:e2e:headless
```

## 🐛 Troubleshooting

### Common Issues

#### 1. Cypress Installation Issues

```bash
# Clear Cypress cache
npx cypress cache clear

# Reinstall Cypress
npm uninstall cypress
npm install cypress --save-dev

# Verify installation
npx cypress verify
```

#### 2. Test Timeouts

```javascript
// Increase timeout for specific test
cy.get('#slow-element', { timeout: 20000 }).should('be.visible');

// Global timeout in cypress.config.js
defaultCommandTimeout: 15000
```

#### 3. Flaky Tests

```javascript
// Add retry logic
it('flaky test', { retries: 2 }, () => {
  // Test code
});

// Use proper waits
cy.intercept('POST', '/api/endpoint').as('apiCall');
cy.get('#submit').click();
cy.wait('@apiCall');
```

#### 4. Browser Compatibility Issues

```javascript
// Check browser support
cy.window().then((win) => {
  if (win.navigator.userAgent.includes('Firefox')) {
    // Firefox-specific code
  }
});
```

### Debug Mode

```bash
# Run Cypress in debug mode
DEBUG=cypress:* npx cypress run

# Run specific test with debug
npx cypress run --spec "cypress/e2e/debug-test.cy.js" --headed --no-exit
```

### Performance Issues

```bash
# Run tests with performance monitoring
npm run test:e2e -- --config video=false,screenshotOnRunFailure=false

# Check memory usage
node --max-old-space-size=4096 node_modules/.bin/cypress run
```

## 🤝 Contributing

### Code Style Guidelines

- Use ESLint configuration provided
- Follow Page Object Model for E2E tests
- Write descriptive test names
- Include both positive and negative test cases
- Add comments for complex test logic

### Pull Request Process

1. **Fork the repository**
2. **Create feature branch**: `git checkout -b feature/new-tests`
3. **Write tests**: Follow existing patterns
4. **Run test suite**: `npm test`
5. **Fix linting issues**: `npm run lint:fix`
6. **Submit PR**: Include test coverage information

### Test Coverage Goals

- **Unit Tests**: 80%+ code coverage
- **E2E Tests**: 100% critical path coverage
- **Cross-Browser**: Chrome, Firefox, Edge support
- **Accessibility**: WCAG 2.1 AA compliance

### Adding New Test Categories

1. Create new test file in appropriate directory
2. Follow naming convention: `feature-name.cy.js` or `feature-name.test.js`
3. Add to test suite documentation
4. Update CI/CD pipeline if needed
