import 'styled-components';
import {BaseTheme} from './types/theme';
// Extend the styled-components DefaultTheme interface
declare module 'styled-components' {
    export interface DefaultTheme extends BaseTheme {
        activeTab?: string;
        sizing: {
            spacing: {
                xs: string;
                sm: string;
                md: string;
                lg: string;
                xl: string;
            };
            borderRadius: {
                sm: string;
                md: string;
                lg: string;
            };
            console: {
                minHeight: string;
                maxHeight: string;
                padding: string;
            };
        };
        typography: {
            fontFamily: string;
            fontSize: {
                xs: string;
                sm: string;
                md: string;
                lg: string;
                xl: string;
            };
            fontWeight: {
                regular: number;
                medium: number;
                bold: number;
            };
            console: {
                fontFamily: string;
                fontSize: string;
                lineHeight: string;
            };
        };
        logging: {
            colors: {
                error: string;
                warning: string;
                info: string;
                debug: string;
                success: string;
                trace: string;
                verbose: string;
                system: string;
            };
            fontSize: {
                normal: string;
                large: string;
                small: string;
                system: string;
            };
            padding: {
                message: string;
                container: string;
                timestamp: string;
            };
            background: {
                error: string;
                warning: string;
                info: string;
                debug: string;
                success: string;
                system: string;
            };
            border: {
                radius: string;
                style: string;
                width: string;
            };
            timestamp: {
                format: string;
                color: string;
                show: boolean;
            };
        };
        colors: {
            primary: string;
            secondary: string;
            background: string;
            text: {
                primary: string;
                secondary: string;
            };
            border: string;
            surface: string;
            error: string;
            success: string;
            warning: string;
            info: string;
            primaryDark?: string;
            secondaryDark?: string;
            errorDark?: string;
            successDark?: string;
            hover?: string;
            disabled: string;
        };
    }
}