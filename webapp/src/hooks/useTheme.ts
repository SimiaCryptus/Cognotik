import React, {useCallback} from 'react';
import {useDispatch, useSelector} from 'react-redux';
import {ThemeName} from '../types';
import {setTheme} from '../store/slices/uiSlice';
import {RootState} from '../store';
import {themeStorage} from '../services/appConfig';

export const useTheme = (initialTheme?: ThemeName): [ThemeName, (theme: ThemeName) => void] => {

    const dispatch = useDispatch();
    const currentTheme = useSelector((state: RootState) => state.ui.theme);
    // Load saved theme on mount
    React.useEffect(() => {
        const savedTheme = themeStorage.getTheme();
        if (savedTheme && savedTheme !== currentTheme) {
            console.info('Theme loaded from storage:', savedTheme);
            dispatch(setTheme(savedTheme));
        }
    }, []);

    const updateTheme = useCallback(
        (newTheme: ThemeName) => {
            console.info('Theme changed:', {from: currentTheme, to: newTheme});
            dispatch(setTheme(newTheme));
            themeStorage.setTheme(newTheme);
        },
        [dispatch]
    );




    // Use initialTheme if provided and no theme is set in state
    React.useEffect(() => {
        const savedTheme = themeStorage.getTheme();
        if (initialTheme && !currentTheme && initialTheme !== savedTheme) {
            updateTheme(initialTheme);
        }
    }, [initialTheme, currentTheme, updateTheme]);
    return [currentTheme, updateTheme];
};