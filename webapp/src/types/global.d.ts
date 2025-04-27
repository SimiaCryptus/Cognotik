declare global {
    interface HTMLElement {
        _contentObserver?: MutationObserver;
    }
}

export type {AppConfig};