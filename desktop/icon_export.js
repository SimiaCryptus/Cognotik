const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const toIco = require('to-ico');

const inputSvgPath = path.resolve(__dirname, './src/main/resources/toolbarIcon.svg');

const outputDir = path.resolve(__dirname, './src/main/resources/');

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, {recursive: true});
}

const iconSizes = [16, 24, 32, 48, 64, 128, 256];

async function generateIcoFiles() {
    try {
        const svgBuffer = fs.readFileSync(inputSvgPath);

        const pngBuffers = [];

        for (const size of iconSizes) {
            try {
                console.log(`Generating ${size}x${size} icon...`);

                const pngBuffer = await sharp(svgBuffer)
                    .resize(size, size)
                    .png()
                    .toBuffer();

                pngBuffers.push(pngBuffer);

                const singleIcoBuffer = await toIco([pngBuffer]);
                const outputFilePath = path.join(outputDir, `toolbarIcon_${size}x${size}.ico`);
                fs.writeFileSync(outputFilePath, singleIcoBuffer);
                console.log(`Generated individual icon file: ${outputFilePath}`);
            } catch (sizeError) {
                console.error(`Error generating ${size}x${size} icon:`, sizeError);
            }
        }

        try {
            const icoBuffer = await toIco(pngBuffers);

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