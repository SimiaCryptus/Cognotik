const sharp = require('sharp');
const path = require('path');

const inputPath = path.join(__dirname, '../../public/logo.svg');
const outputPath = path.join(__dirname, '../../public/logo256.png');

sharp(inputPath)
    .resize(256, 256)
    .png()
    .toFile(outputPath)
    .then(info => {
        console.log(`Logo converted successfully: ${info.width}x${info.height}px, ${info.size} bytes`);
    })
    .catch(err => {
        console.error(`Logo conversion failed: ${err.message}`);
        process.exit(1);
    });