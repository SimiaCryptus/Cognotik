const StringManipulationUtils = require('../../src/utils/StringManipulationUtils');

describe('StringManipulationUtils', () => {

    describe('trim', () => {
        it('should remove whitespace from both ends of a string', () => {
            expect(StringManipulationUtils.trim('  hello  ')).toBe('hello');
        });

        it('should return the same string if there is no whitespace', () => {
            expect(StringManipulationUtils.trim('hello')).toBe('hello');
        });

        it('should handle empty strings', () => {
            expect(StringManipulationUtils.trim('')).toBe('');
        });
    });

    describe('split', () => {
        it('should split a string by a given delimiter', () => {
            expect(StringManipulationUtils.split('a,b,c', ',')).toEqual(['a', 'b', 'c']);
        });

        it('should return an array with the original string if delimiter is not found', () => {
            expect(StringManipulationUtils.split('abc', ',')).toEqual(['abc']);
        });

        it('should handle empty strings', () => {
            expect(StringManipulationUtils.split('', ',')).toEqual(['']);
        });
    });

    describe('join', () => {
        it('should join an array of strings with a given delimiter', () => {
            expect(StringManipulationUtils.join(['a', 'b', 'c'], ',')).toBe('a,b,c');
        });

        it('should return an empty string for an empty array', () => {
            expect(StringManipulationUtils.join([], ',')).toBe('');
        });

        it('should handle arrays with one element', () => {
            expect(StringManipulationUtils.join(['a'], ',')).toBe('a');
        });
    });

    describe('toUpperCase', () => {
        it('should convert a string to uppercase', () => {
            expect(StringManipulationUtils.toUpperCase('hello')).toBe('HELLO');
        });

        it('should handle empty strings', () => {
            expect(StringManipulationUtils.toUpperCase('')).toBe('');
        });

        it('should not change already uppercase strings', () => {
            expect(StringManipulationUtils.toUpperCase('HELLO')).toBe('HELLO');
        });
    });

    describe('toLowerCase', () => {
        it('should convert a string to lowercase', () => {
            expect(StringManipulationUtils.toLowerCase('HELLO')).toBe('hello');
        });

        it('should handle empty strings', () => {
            expect(StringManipulationUtils.toLowerCase('')).toBe('');
        });

        it('should not change already lowercase strings', () => {
            expect(StringManipulationUtils.toLowerCase('hello')).toBe('hello');
        });
    });

});