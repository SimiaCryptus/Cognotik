"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ErrorHandler = void 0;
/**
 * Handles errors that occur during test execution with appropriate recovery strategies
 */
class ErrorHandler {
    /**
     * Logs an error message
     * @param message The error message to log
     */
    log(message) {
        console.error(`[ERROR] ${message}`);
    }
}
exports.ErrorHandler = ErrorHandler;
exports.default = ErrorHandler;
//# sourceMappingURL=ErrorHandler.js.map