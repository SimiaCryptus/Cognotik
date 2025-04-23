const StringUtils = require('../../src/utils/StringUtils');

describe('StringUtils', () => {
    describe('trim', () => {
        it('should trim whitespace from both ends of a string', () => {
            expect(StringUtils.trim('  hello  ')).toBe('hello');
        });

        it('should return the same string if there is no whitespace to trim', () => {
            expect(StringUtils.trim('hello')).toBe('hello');
        });

        it('should throw a TypeError if the input is not a string', () => {
            expect(() => StringUtils.trim(123)).toThrow(TypeError);
        });
    });

    describe('split', () => {
        it('should split a string into an array of substrings using a specified separator', () => {
            expect(StringUtils.split('a,b,c', ',')).toEqual(['a', 'b', 'c']);
        });

        it('should return an array with the original string if the separator is not found', () => {
            expect(StringUtils.split('abc', ',')).toEqual(['abc']);
        });

        it('should throw a TypeError if the input is not a string', () => {
            expect(() => StringUtils.split(123, ',')).toThrow(TypeError);
        });
    });

    describe('toUpperCase', () => {
        it('should convert a string to uppercase', () => {
            expect(StringUtils.toUpperCase('hello')).toBe('HELLO');
        });

        it('should return the same string if it is already in uppercase', () => {
            expect(StringUtils.toUpperCase('HELLO')).toBe('HELLO');
        });

        it('should throw a TypeError if the input is not a string', () => {
            expect(() => StringUtils.toUpperCase(123)).toThrow(TypeError);
        });
    });

    describe('toLowerCase', () => {
        it('should convert a string to lowercase', () => {
            expect(StringUtils.toLowerCase('HELLO')).toBe('hello');
        });

        it('should return the same string if it is already in lowercase', () => {
            expect(StringUtils.toLowerCase('hello')).toBe('hello');
        });

        it('should throw a TypeError if the input is not a string', () => {
            expect(() => StringUtils.toLowerCase(123)).toThrow(TypeError);
        });
    });
});