// cypress/support/e2e.js
import './commands';

// Hide fetch/XHR requests from command log
const app = window.top;
if (!app.document.head.querySelector('[data-hide-command-log-request]')) {
    const style = app.document.createElement('style');
    style.innerHTML = '.command-name-request, .command-name-xhr { display: none }';
    style.setAttribute('data-hide-command-log-request', '');
    app.document.head.appendChild(style);
}

// Global error handling
Cypress.on('uncaught:exception', (err, runnable) => {
    // Prevent Cypress from failing the test on uncaught exceptions
    // that might be expected in error handling tests
    if (err.message.includes('API key') || err.message.includes('Network Error')) {
        return false;
    }
    return true;
});

// Add custom assertions
chai.use((chai, utils) => {
    chai.Assertion.addMethod('containOption', function (expected) {
        const obj = this._obj;
        const options = Array.from(obj.find('option')).map(opt => opt.textContent);
        this.assert(
            options.includes(expected),
            `expected select to contain option "${expected}", but got: ${options.join(', ')}`,
            `expected select not to contain option "${expected}"`
        );
    });
});