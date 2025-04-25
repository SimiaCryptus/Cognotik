const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const toIco = require('to-ico');

// Input SVG file path
const inputSvgPath = path.resolve(__dirname, './src/main/resources/toolbarIcon.svg');
// Output directory for .ico files
const outputDir = path.resolve(__dirname, './src/main/resources/');

// Ensure output directory exists
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, {recursive: true});
}

// Icon sizes to generate
const iconSizes = [16, 24, 32, 48, 64, 128, 256];

async function generateIcoFiles() {
    try {
        const svgBuffer = fs.readFileSync(inputSvgPath);
        // Generate individual .ico files for each size
        const pngBuffers = [];

        for (const size of iconSizes) {
            try {
                console.log(`Generating ${size}x${size} icon...`);

                // Generate PNG buffer for this size
                const pngBuffer = await sharp(svgBuffer)
                    .resize(size, size)
                    .png()
                    .toBuffer();

                pngBuffers.push(pngBuffer);

                // Create individual .ico file for this size
                const singleIcoBuffer = await toIco([pngBuffer]);
                const outputFilePath = path.join(outputDir, `toolbarIcon_${size}x${size}.ico`);
                fs.writeFileSync(outputFilePath, singleIcoBuffer);
                console.log(`Generated individual icon file: ${outputFilePath}`);
            } catch (sizeError) {
                console.error(`Error generating ${size}x${size} icon:`, sizeError);
            }
        }

        // Convert PNG buffers to single .ico buffer
        try {
            const icoBuffer = await toIco(pngBuffers);
            // Write the combined .ico file (containing all sizes)
            const icoPath = path.join(outputDir, 'toolbarIcon.ico');
            fs.writeFileSync(icoPath, icoBuffer);
            console.log(`Generated combined icon file: ${icoPath}`);
        } catch (combinedError) {
            console.error('Error generating combined icon file:', combinedError);
        }

        console.log('Icon generation complete!');
    } catch (error) {
        console.error('Error generating icon files:', error);
        process.exit(1);
    }
}

generateIcoFiles();