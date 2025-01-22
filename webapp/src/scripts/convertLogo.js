const sharp = require('sharp');
const path = require('path');

const inputPath = path.join(__dirname, '../../public/logo.svg');
const outputPath = path.join(__dirname, '../../public/logo256.png');

sharp(inputPath)
  .resize(256, 256)
  .png()
  .toFile(outputPath)
  .then(info => {
    console.log('Successfully converted logo.svg to logo256.png');
    console.log(info);
  })
  .catch(err => {
    console.error('Error converting logo:', err);
  });