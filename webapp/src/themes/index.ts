import {themes} from './themes';
import type {ThemeName} from '../types/theme';

export type {ThemeName};
const themeCount = Object.keys(themes).length;

if (themeCount === 0) {
    console.error('Critical: No themes available - application styling will be broken');
} else if (!themes['default']) {
    console.error('Critical: No default theme found - application may have styling issues');
}

if (process.env.NODE_ENV === 'development') {
    console.debug(`Theme system initialized with ${themeCount} themes`);
}

export {themes};