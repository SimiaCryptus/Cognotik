declare module "qrcode-generator" {
    export interface QRCode {
        /** Adds data to be encoded in the QR code */
        addData(data: string): void;

        /** Generates the QR code with the provided data */

        make(): void;

        /** Creates an HTML img tag containing the QR code
         * @param cellSize - Optional size of each QR code cell in pixels
         * @param margin - Optional margin around the QR code in pixels
         * @returns HTML img tag string
         */

        createImgTag(cellSize?: number, margin?: number): string;
        /** Creates a data URL containing the QR code
         * @param cellSize - Optional size of each QR code cell in pixels
         * @param margin - Optional margin around the QR code in pixels
         * @returns Data URL string
         */

        createDataURL(cellSize?: number, margin?: number): string;
        /** Creates an SVG tag containing the QR code
         * @param cellSize - Optional size of each QR code cell in pixels
         * @param margin - Optional margin around the QR code in pixels
         * @returns SVG tag string
         */

        createSvgTag(cellSize?: number, margin?: number): string;
        /** Creates an ASCII art representation of the QR code
         * @param cellSize - Optional size of each cell in characters
         * @param margin - Optional margin around the QR code in characters
         * @returns ASCII string representation
         */

        createASCII(cellSize?: number, margin?: number): string;
        /** Gets the number of modules (cells) per side in the QR code
         * @returns Module count
         */

        getModuleCount(): number;
        /** Checks if a module at the specified position is dark
         * @param row - Row index
         * @param col - Column index
         * @returns True if the module is dark, false otherwise
         */

        isDark(row: number, col: number): boolean;
    }

    export interface QRCodeGenerator {
        QRCode: QRCode;
        TypeNumber: 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 22 | 23 | 24 | 25 | 26 | 27 | 28 | 29 | 30 | 31 | 32 | 33 | 34 | 35 | 36 | 37 | 38 | 39 | 40;
        ErrorCorrectionLevel: {
            /** Low error correction - 7% */
            L: 'L';
            /** Medium error correction - 15% */
            M: 'M';
            /** Quartile error correction - 25% */
            Q: 'Q';
            /** High error correction - 30% */
            H: 'H';
        };

        /** Creates a new QR Code instance
         * @param typeNumber - QR Code version (1-40)
         * @param errorCorrectionLevel - Error correction level ('L', 'M', 'Q', 'H')
         * @returns QR Code instance
         */

        (typeNumber?: number, errorCorrectionLevel?: string): QRCode;
    }

    const qrcode: QRCodeGenerator;
    export = qrcode;
}