import {themes} from './themes';

export type {ThemeName} from '../types';

const themeCount = Object.keys(themes).length;
// Log theme initialization status
if (process.env.NODE_ENV !== 'production') {
    console.log(`Theme system initialized with ${themeCount} themes`);
}


export {themes};