const StringManipulationUtils = require('../src/utils/StringManipulationUtils');

describe('StringManipulationUtils', () => {

    test('trim should remove whitespace from both ends of a string', () => {
        expect(StringManipulationUtils.trim('  hello  ')).toBe('hello');
    });

    test('split should divide a string into an array by the given delimiter', () => {
        expect(StringManipulationUtils.split('a,b,c', ',')).toEqual(['a', 'b', 'c']);
    });

    test('join should join an array of strings with the given delimiter', () => {
        expect(StringManipulationUtils.join(['a', 'b', 'c'], ',')).toBe('a,b,c');
    });

    test('toUpperCase should convert a string to uppercase', () => {
        expect(StringManipulationUtils.toUpperCase('hello')).toBe('HELLO');
    });

    test('toLowerCase should convert a string to lowercase', () => {
        expect(StringManipulationUtils.toLowerCase('HELLO')).toBe('hello');
    });

});