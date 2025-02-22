import 'styled-components';
import {BaseTheme} from './types/theme';
// Extend the styled-components DefaultTheme interface
declare module 'styled-components' {
    export interface DefaultTheme extends BaseTheme {
        // Add any additional theme properties specific to styled-components here
    }
}