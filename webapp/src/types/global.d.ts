// Extend the Window interface to include the mermaid property
declare global {
    interface HTMLElement {
        _contentObserver?: MutationObserver;
        // Add JSDoc comment explaining the purpose of _contentObserver
        /**
         * MutationObserver instance used to watch for content changes
         * @internal This is intended for internal use only
         */
    }
}
// Export AppConfig type with proper type definition
interface AppConfig {
    // Add AppConfig interface properties here
}


 export type {AppConfig};