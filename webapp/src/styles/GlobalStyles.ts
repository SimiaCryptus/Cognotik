import type {DefaultTheme} from 'styled-components';
import {createGlobalStyle} from 'styled-components';

// Enhanced logging function with timestamp
const logStyleChange = (component: string, property: string, value: any) => {
    // Exit if not in development environment
    if (process.env.NODE_ENV !== 'development') {
        return;
    }
    const timestamp = new Date().toISOString();
    const criticalEvents = [
        'theme-transition',
        'theme-change',
        'font-load',
        'style-init',
        'accessibility-violation'
    ];
    // Only log critical style changes
    if (criticalEvents.some(event => property.includes(event))) {
        console.log(`[${timestamp}] GlobalStyles: ${component} - ${property}:`, value);
    }
};

// Add logging for initial style load
logStyleChange('GlobalStyles', 'style-init', 'Styles initialized');

export const GlobalStyles = createGlobalStyle<{ theme: DefaultTheme; }>`
    /* Improved scrollbar styling */
    ::-webkit-scrollbar {
        width: 10px;
    }

    ::-webkit-scrollbar-track {
        background: ${({theme}) => theme.colors.background};
        border-radius: 4px;
    }

    ::-webkit-scrollbar-thumb {
        background: ${({theme}) => theme.colors.primary + '40'};
        border-radius: 4px;
        border: 2px solid ${({theme}) => theme.colors.background};

        &:hover {
            background: ${({theme}) => theme.colors.primary + '60'};
        }
    }

    :root {
        /* Theme variables are now set dynamically in ThemeProvider */
        /* Font weights */
        --font-weight-light: 300;
        --font-weight-regular: 400;
        --font-weight-medium: 500;
        --font-weight-semibold: 600;
        --font-weight-bold: 700;
        --font-weight-extrabold: 800;
        /* Font families */
        --font-primary: 'Outfit', system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
        --font-heading: 'Space Grotesk', system-ui, sans-serif;
        --font-mono: 'IBM Plex Mono', 'Fira Code', monospace;
        --font-display: 'Syne', system-ui, sans-serif;
        /* Font sizes */
        --font-size-xs: clamp(0.75rem, 1.5vw, 0.875rem);
        --font-size-sm: clamp(0.875rem, 1.75vw, 1rem);
        --font-size-md: clamp(1rem, 2vw, 1.125rem);
        --font-size-lg: clamp(1.25rem, 2.5vw, 1.75rem);
        --font-size-xl: clamp(1.75rem, 3.5vw, 2.5rem);
        --font-size-2xl: clamp(2.5rem, 5vw, 3.5rem);
        /* Line heights */
        --line-height-tight: 1.15;
        --line-height-normal: 1.65;
        --line-height-relaxed: 1.85;
        /* Letter spacing */
        --letter-spacing-tight: -0.04em;
        --letter-spacing-normal: -0.02em;
        --letter-spacing-wide: 0.04em;
        --letter-spacing-wider: 0.08em;
    }

    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');
    @import url('https://fonts.googleapis.com/css2?family=Poppins:wght@500;600;700;800&family=Raleway:wght@600;700;800&display=swap');
    @import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600&display=swap');
    @import url('https://fonts.googleapis.com/css2?family=Montserrat:wght@600;700;800&display=swap');
    ${() => { logStyleChange('Fonts', 'font-load', 'Web fonts loaded'); return ''; }}

    /* Override Prism.js theme colors to match current theme */
    .token.comment,
    .token.prolog,
    .token.doctype,
    .token.cdata {
        color: var(--theme-text-secondary);
    }

    .token.punctuation {
        color: var(--theme-text);
    }

    .token.property,
    .token.tag,
    .token.constant,
    .token.symbol {
        color: var(--theme-primary);
    }

    .token.boolean,
    .token.number {
        color: ${({theme}) => theme.colors.warning};
    }

    .token.selector,
    .token.string {
        color: ${({theme}) => theme.colors.success};
    }

    .token.operator,
    .token.keyword {
        color: ${({theme}) => theme.colors.info};
    }


    /* Reset styles */
    * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
    }

    /* Optimize performance for animated elements */
    .animated {
        transform: translate3d(0, 0, 0);
        backface-visibility: hidden;
        perspective: 1000;
        will-change: transform;
    }

    /* Enhanced list styling */
    ul, ol {
        padding-left: 2em;
        margin: 1em 0;
        list-style-position: outside;
        color: ${({theme}) => theme.colors.text.primary};
        font-size: 0.95em;
    }

    /* Improve nested list spacing and styling */
    ul ul, ul ol, ol ul, ol ol {
        margin: 0.75em 0 0.75em 0.5em;
        padding-left: 1.5em;
        border-left: 1px solid ${({theme}) => theme.colors.border + '40'};
        position: relative;
        font-size: 0.95em;
    }

    /* List item styling */
    li {
        margin: 0.5em 0;
        line-height: 1.6;
        position: relative;
        padding-left: 0.5em;
        transition: all 0.2s ease;
    }

    /* List item hover effect */
    li:hover {
        color: ${({theme}) => theme.colors.primary};
        transform: translateX(2px);
    }

    /* Custom bullets for unordered lists */
    ul {
        list-style: none;
    }

    ul li::before {
        position: absolute;
        left: -1.5em;
        top: 0.7em;
        border-radius: 50%;
        transition: all 0.3s ease;
        box-shadow: 0 0 2px ${({theme}) => theme.colors.primary + '40'};
        color: ${({theme}) => theme.colors.primary};
    }

    /* Nested unordered list bullets */
    ul ul li::before {
        width: 5px;
        height: 5px;
        left: -1.3em;
        opacity: 0.9;
        box-shadow: none;
    }

    ul ul ul li::before {
        width: 4px;
        height: 4px;
        left: -1.2em;
        opacity: 0.7;
    }

    /* Ordered list styling */
    ol {
        counter-reset: item;
        list-style: none;
        padding-left: 2.5em;
    }

    ol li {
        counter-increment: item;
        padding-left: 0.25em;
    }

    ol li::before {
        content: counter(item) ".";
        position: absolute;
        left: -2.25em;
        width: 1.5em;
        text-align: right;
        color: ${({theme}) => theme.colors.primary + 'E6'};
        font-weight: 600;
        font-feature-settings: "tnum";
        transition: all 0.3s ease;
        font-size: 0.9em;
    }

    /* Nested ordered list counters */
    ol ol {
        counter-reset: subitem;
        border-left: 1px solid ${({theme}) => theme.colors.border + '40'};
        margin-left: 0.5em;
        padding-left: 2em;
        list-style-type: none;
    }

    ol ol li {
        counter-increment: subitem;
        position: relative;
        padding-left: 0.5em;
        list-style-type: none;

        &::marker {
            display: none;
        }
    }

    ol ol li::before {
        content: counter(item) "." counter(subitem);
        position: absolute;
        left: -2.75em;
        width: 2.75em;
        text-align: right;
        color: ${({theme}) => theme.colors.secondary + 'CC'};
        font-size: 0.85em;
        opacity: 0.9;
        top: 0;
        display: inline-block;
        font-variant-numeric: tabular-nums;
        font-feature-settings: "tnum";
    }

    /* Third level ordered lists */
    ol ol ol {
        counter-reset: subsubitem;
        padding-left: 2.5em;
        list-style-type: none;

        &::marker {
            display: none;
        }
    }

    ol ol ol li {
        counter-increment: subsubitem;
        padding-left: 0.5em;
        list-style-type: none;

        &::marker {
            display: none;
        }
    }

    ol ol ol li::before {
        content: counter(item) "." counter(subitem) "." counter(subsubitem);
        width: 4em;
        left: -4em;
        top: 0;
        display: inline-block;
        font-variant-numeric: tabular-nums;
        font-feature-settings: "tnum";
        white-space: nowrap;
    }

    /* List spacing in content areas */
    .message-content ul,
    .message-content ol {
        margin: 1em 0;
        padding: 1em 1.25em 1em 2.5em;
        background: ${({theme}) => theme.colors.background + '08'};
        border-radius: 8px;
        border: 1px solid ${({theme}) => theme.colors.border + '20'};
        box-shadow: 0 2px 4px ${({theme}) => theme.colors.border + '10'};
    }

    /* List item hover effects */
    ul li:hover::before {
        transform: scale(1.3);
        background-color: ${({theme}) => theme.colors.primary};
        box-shadow: 0 0 4px ${({theme}) => theme.colors.primary + '40'};
    }

    /* Improve nested list visual hierarchy */
    ul ul, ol ol {
        opacity: 1;
        background: ${({theme}) => theme.colors.background + '05'};
    }

    ul ul ul, ol ol ol {
        opacity: 1;
        background: ${({theme}) => theme.colors.background + '03'};
    }

    /* Improve list item text selection */
    li::selection {
        background-color: ${({theme}) => theme.colors.primary + '40'};
    }

    /* List animations on theme change */
    ul li::before,
    ol li::before {
        transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
    }

    /* Theme variables */

    :root {
    }

    /* Improve focus styles globally */
    *:focus-visible {
        outline: 2px solid ${({theme}) => theme.colors.primary};
        outline-offset: 2px;
    }

    /* Loading Spinner Styles */
    .spinner-border {
        display: inline-block;
       --spinner-diameter: 2rem;
       inline-size: var(--spinner-diameter);
       block-size: var(--spinner-diameter);
        vertical-align: text-bottom;
       border-width: calc(var(--spinner-diameter) * 0.125);
       border-style: solid;
       border-color: currentColor;
       color: ${({theme}) => theme.colors.primary};
        border-right-color: transparent;
        border-radius: 50%;
       animation: spinner-border 0.75s linear infinite;
       aspect-ratio: 1;
       box-sizing: border-box;
       flex-shrink: 0;
       min-inline-size: var(--spinner-diameter);
       min-block-size: var(--spinner-diameter);
       max-inline-size: var(--spinner-diameter);
       max-block-size: var(--spinner-diameter);
       will-change: transform;
       transform-origin: center center;
       position: relative;
       contain: size layout;
       isolation: isolate;
    }
    /* Loading states */
    .message-list-loading {
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 2rem;
    }
    .connection-status {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem;
        justify-content: center;
        color: ${({theme}) => theme.colors.text};
        background: ${({theme}) => theme.colors.background};
    }
   /* Size variants for spinner */
   .spinner-border.small {
       --spinner-diameter: 1rem;
   }
   .spinner-border.large {
       --spinner-diameter: 3rem;
   }

    @keyframes spinner-border {
        to {
           transform: rotate(360deg) translateZ(0);
        }
    }
   /* Ensure spinner container maintains aspect ratio */
   [role="status"] {
       display: inline-flex;
       align-items: center;
       justify-content: center;
       aspect-ratio: 1;
       contain: layout;
       aspect-ratio: 1;
       position: relative;
   }

    /* Screen reader only text */
    .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
    }

    /* Loading container styles */
    [role="status"] {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    /* Improve button accessibility */
    button {
        font-family: inherit;
        font-size: inherit;
        line-height: inherit;
    }

    /* Message content theme transitions */
    .message-content {
        color: var(--theme-text);
        background: var(--theme-background);
        border-color: var(--theme-border);
    }

    .message-content pre,
    .message-content code {
        background: var(--theme-surface);
        color: var(--theme-text);
    }

    /* Universal code block styles using CSS variables */
    pre code {
        background: var(--theme-surface);
        color: var(--theme-text);
        border-color: var(--theme-border);
    }

    body {
        font-family: var(--font-primary);
        font-weight: var(--font-weight-regular);
        background-color: var(--theme-background);
        color: var(--theme-text);
        line-height: var(--line-height-normal);
        font-size: var(--font-size-md);
        letter-spacing: var(--letter-spacing-normal);
        text-rendering: optimizeLegibility;
        overflow-x: hidden;
        min-height: 100vh;
        font-feature-settings: "liga" 1, "kern" 1;
    }

    /* Heading styles */
    h1, h2, h3, h4, h5, h6 {
        font-family: var(--font-display);
        font-weight: var(--font-weight-extrabold);
        letter-spacing: var(--letter-spacing-tight);
        line-height: var(--line-height-tight);
        margin: 2em 0 1em;
        text-transform: uppercase;
        background: ${({theme}) => `
            linear-gradient(135deg,
                ${theme.colors.primary},
                ${theme.colors.secondary}
            )
        `};
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        position: relative;
        z-index: 1;
        display: inline-block;

        /* Add decorative underline */

        &::after {
            content: '';
            position: absolute;
            bottom: -0.35em;
            left: 0;
            width: 100%;
            height: 0.12em;
            background: ${({theme}) => `
                linear-gradient(90deg,
                    ${theme.colors.primary}40,
                    ${theme.colors.secondary}40
                )
            `};
            border-radius: 4px;
            transition: all 0.3s ease;
            transform: scaleX(0.3);
            transform-origin: left;
        }

        &:hover::after {
            transform: scaleX(1);
            background: ${({theme}) => `
                linear-gradient(90deg,
                    ${theme.colors.primary},
                    ${theme.colors.secondary}
                )
            `};
        }
    }

    /* Individual heading sizes */
    h1 {
        font-size: var(--font-size-2xl);
        margin-top: 1em;
        padding-bottom: 0.5em;
    }

    h2 {
        font-size: var(--font-size-xl);
        padding-bottom: 0.4em;
    }

    h3 {
        font-size: var(--font-size-lg);
        padding-bottom: 0.3em;
    }

    h4 {
        font-size: var(--font-size-md);
        font-weight: var(--font-weight-bold);
        padding-bottom: 0.2em;
        text-transform: none;
    }

    h5 {
        font-size: var(--font-size-sm);
        font-weight: var(--font-weight-semibold);
        text-transform: none;
    }

    h6 {
        font-size: var(--font-size-xs);
        font-weight: var(--font-weight-medium);
        letter-spacing: var(--letter-spacing-wide);
        text-transform: none;
    }

    /* Add hover effect for headings */
    h1:hover, h2:hover, h3:hover, h4:hover, h5:hover, h6:hover {
        transform: translate3d(4px, 0, 0);
        transition: transform 0.3s cubic-bezier(0.2, 0, 0.2, 1);
    }

    /* Improve heading accessibility */
    h1:focus-visible,
    h2:focus-visible,
    h3:focus-visible,
    h4:focus-visible,
    h5:focus-visible,
    h6:focus-visible {
        outline: none;
        box-shadow: 0 0 0 3px ${({theme}) => theme.colors.primary}40;
        border-radius: 4px;
    }

    /* Add spacing after headings when followed by text */
    h1 + p,
    h2 + p,
    h3 + p,
    h4 + p,
    h5 + p,
    h6 + p {
        margin-top: 1em;
    }

    /* Code styles */
    code, pre {
        font-family: var(--font-mono);
        font-weight: 600;
        font-feature-settings: "liga" 0;
        font-size: 0.9em;
        line-height: var(--line-height-relaxed);
        letter-spacing: -0.01em;
        font-variant-ligatures: contextual;
        border-radius: 6px;
        padding: 0.2em 0.4em;
    }


    pre {
        border-radius: 12px !important;
        padding: 1.5em !important;
        margin: 1.5em 0 !important;
        overflow: auto;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
        font-family: 'Fira Code', Consolas, Monaco, monospace !important;
        font-size: 0.9em !important;
        line-height: 1.6 !important;
        border: 1px solid ${({theme}) => theme.colors.border + '30'};
    }


    .chat-input {
        background-color: ${({theme}: { theme: DefaultTheme }) => theme.colors.surface};
        color: ${({theme}: { theme: DefaultTheme }) => theme.colors.text.primary};
        border-radius: ${({theme}: { theme: DefaultTheme }) => theme.sizing.borderRadius.md};
        padding: 10px;
        margin-bottom: 10px;
        overflow: auto;
        resize: vertical;
        border: 1px solid ${({theme}: { theme: DefaultTheme }) => theme.colors.border};
        box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
        font-size: 16px;
        transition: border-color 0.3s ease;
        min-height: 40px;
    }

    .chat-input:focus {
        outline: none;
        border-color: ${({theme}: { theme: DefaultTheme }) => theme.colors.primary};
        box-shadow: 0 0 5px rgba(0, 123, 255, 0.5);
    }

    button {
        font-family: inherit;
        cursor: pointer;
    }

    input, textarea {
        font-family: inherit;
        color: ${({theme}) => theme.colors.text.primary};

        &::placeholder {
            color: ${({theme}) => theme.colors.text.secondary};
        }
    }

    /* Transitions for theme switching */
    body, button, input, textarea {
        transition: background-color 0.2s cubic-bezier(0.2, 0, 0.2, 1),
        color 0.2s cubic-bezier(0.2, 0, 0.2, 1);
    }

    /* Log when transitions complete */
    body {
        &:after {
            content: '';
            transition: background-color 0.3s ease;
            opacity: 0;
        }

        &.theme-transition-complete:after {
            opacity: 1;
            ${() => {
                logStyleChange('body', 'theme-transition', 'completed');
                return '';
            }}
        }
    }

    .cmd-button {
        background-color: ${({theme}) => theme.colors.primary};
        color: #fff;
        border: none;
        border-radius: 5px;
        box-shadow: ${({theme}) => theme.shadows.medium};
        transition: all ${({theme}) => theme.transitions?.default} var(--transition-timing);
        /* Inherit base styles from App.css */
        composes: cmd-button from global;
    }

    .cmd-button:hover {
        background-color: ${({theme}) => theme.colors.primaryDark};
        transform: translateY(-2px);
        box-shadow: ${({theme}) => theme.shadows?.large};
    }

    .cmd-button:active {
        transform: translateY(0);
        box-shadow: ${({theme}) => theme.shadows.medium};
    }


    .verbose-wrapper {
        display: none;
        transition: all 0.3s ease;
    }
    .verbose-wrapper.verbose-visible {
        display: inline !important;
    }
    /* Response header styles: improves the look and feel of response headers */
    .response-header {
        background-color: ${({theme}) => theme.colors.surface};
        border-bottom: 2px solid ${({theme}) => theme.colors.primary};
        padding: 1rem;
        margin-bottom: 1rem;
        font-family: var(--font-heading);
        color: ${({theme}) => theme.colors.text.primary};
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        border-radius: 8px;
        transition: background-color 0.3s ease, box-shadow 0.3s ease;
    }
    .response-header:hover {
        background-color: ${({theme}) => theme.colors.background};
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
    }
`;