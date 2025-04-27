import 'styled-components';
import {BaseTheme} from './types/theme';

declare module 'styled-components' {
    export interface DefaultTheme extends BaseTheme {

    }
}