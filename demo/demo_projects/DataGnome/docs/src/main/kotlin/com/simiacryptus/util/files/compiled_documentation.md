# IntArrayAppendFile.kt

## IntArrayAppendFile API Documentation

### Overview

The `IntArrayAppendFile` class is a utility for managing a file that stores an array of integers. It provides
functionality to append integers to the file and read integers from specific indices. This class ensures that the file
is not too large and handles file operations efficiently using buffered streams.

### Package

`com.simiacryptus.util.files`

### Class: IntArrayAppendFile

#### Constructor

- `IntArrayAppendFile(file: File)`
    - **Parameters:**
        - `file`: An instance of `java.io.File` representing the file to be used for storing the integer array.
    - **Description:** Initializes a new `IntArrayAppendFile` object with the specified file. It checks that the file
      size is within acceptable limits and initializes the length of the integer array based on the file size.

#### Properties

- `isClosed: Boolean`
    - **Description:** Indicates whether the file has been closed. Once closed, no further operations can be performed
      on the file.

- `length: XElements`
    - **Description:** Represents the number of integers currently stored in the file. This is calculated as the file
      length divided by 4 (since each integer is 4 bytes).

#### Methods

- `append(value: Int)`
    - **Parameters:**
        - `value`: The integer value to append to the file.
    - **Description:** Appends the given integer to the end of the file. If the file is closed, it throws an
      `IllegalStateException`. The method converts the integer to bytes and writes it to the file using a buffered
      output stream, then updates the length of the array.

- `read(index: Int): Int`
    - **Parameters:**
        - `index`: The index of the integer to read from the file.
    - **Returns:** The integer value at the specified index.
    - **Description:** Reads an integer from the specified index in the file. If the index is out of bounds, it throws
      an `IndexOutOfBoundsException`. The method uses a `RandomAccessFile` to seek to the correct position and read the
      integer.

- `close()`
    - **Description:** Closes the file, preventing any further read or write operations. It sets the `isClosed` flag to
      true and closes the buffered output stream.

#### Companion Object

- The `IntArrayAppendFile` class includes a companion object, which currently does not contain any members or methods.

### Usage Example

```kotlin
val file = File("integers.dat")
val intArrayFile = IntArrayAppendFile(file)

intArrayFile.append(42)
intArrayFile.append(100)

val firstValue = intArrayFile.read(0)

val secondValue = intArrayFile.read(1)


intArrayFile.close()
```

### Notes

- The file should not exceed `Int.MAX_VALUE` bytes in size, as enforced by the constructor.
- Each integer is stored as 4 bytes in the file.
- The class uses lazy initialization for the buffered output stream to optimize performance.
- Ensure to close the file using the `close()` method to release resources properly.

# IntArrayMappedFile.kt

## IntArrayMappedFile API Documentation

### Overview

The `IntArrayMappedFile` class provides a mechanism to manage a file as a memory-mapped array of integers. This allows
for efficient reading and writing of integer data directly to and from a file, leveraging the capabilities of Java's
`MappedByteBuffer` and `FileChannel`.

### Class: IntArrayMappedFile

#### Constructor

- **IntArrayMappedFile(file: File)**
    - Initializes a new instance of the `IntArrayMappedFile` class, mapping the specified file for read and write
      operations.
    - **Parameters:**
        - `file`: The `File` object representing the file to be mapped.

#### Properties

- **length: XElements**
    - Returns the number of integers in the file. The length is calculated by dividing the file size by 4 (since each
      integer is 4 bytes).
    - **Throws:**
        - `IllegalArgumentException` if the file is empty or too large to be handled as an integer array.

#### Methods

- **get(pos: XElements): Int**
    - Retrieves the integer at the specified position in the mapped file.
    - **Parameters:**
        - `pos`: The position (as `XElements`) from which to read the integer.
    - **Returns:**
        - The integer value at the specified position.

- **set(pos: XElements, value: Int)**
    - Sets the integer at the specified position in the mapped file.
    - **Parameters:**
        - `pos`: The position (as `XElements`) where the integer should be written.
        - `value`: The integer value to write.

- **flush()**
    - Forces any changes made to the mapped byte buffer to be written to the file.

- **close()**
    - Closes the file channel, releasing any resources associated with it.

- **append(value: Int)**
    - Appends an integer to the end of the file, extending the file size by 4 bytes.
    - **Parameters:**
        - `value`: The integer value to append.

- **allocate(newLength: XElements)**
    - Allocates additional space in the file to accommodate a new length of integers.
    - **Parameters:**
        - `newLength`: The new length (as `XElements`) of the integer array.
    - **Throws:**
        - `IllegalArgumentException` if the new length is not greater than the current length.

- **fill(value: Int)**
    - Fills the entire mapped file with the specified integer value.
    - **Parameters:**
        - `value`: The integer value to fill the file with.

#### Companion Object

- The companion object is currently empty but can be used for static methods or constants related to the
  `IntArrayMappedFile` class.

### Usage Example

```kotlin
val file = File("data.dat")
val intArrayFile = IntArrayMappedFile(file)

intArrayFile.set(XElements(0), 42)

val value = intArrayFile.get(XElements(0))

intArrayFile.append(99)

intArrayFile.fill(0)

intArrayFile.flush()

intArrayFile.close()
```

### Notes

- The `IntArrayMappedFile` class is designed for use cases where large arrays of integers need to be stored and accessed
  efficiently.
- It is important to manage resources properly by closing the file channel when done to avoid resource leaks.
- The class assumes that the file size is a multiple of 4 bytes, as each integer occupies 4 bytes.

# LongArrayMappedFile.kt

## LongArrayMappedFile Class

The `LongArrayMappedFile` class provides a utility for managing a file-backed array of long integers using memory-mapped
file I/O. This class allows efficient reading and writing of long values to a file, leveraging the capabilities of
Java's `MappedByteBuffer` and `FileChannel`.

### Package

`com.simiacryptus.util.files`

### Constructor

- `LongArrayMappedFile(file: File)`
    - Initializes a new instance of the `LongArrayMappedFile` class with the specified file.
    - **Parameters:**
        - `file`: The `File` object representing the file to be used for memory mapping.

### Properties

- `length: XElements`
    - Returns the number of long elements in the file.
    - **Throws:**
        - `IllegalArgumentException` if the file is empty or too large.

### Private Properties

- `channel: FileChannel`
    - Lazily initialized `FileChannel` for reading and writing to the file.
- `_mappedByteBuffer: MappedByteBuffer?`
    - Holds the current memory-mapped byte buffer.
- `mappedByteBuffer: MappedByteBuffer`
    - Provides access to the memory-mapped byte buffer, initializing it if necessary.

### Methods

#### Public Methods

- `get(pos: XElements): Long`
    - Retrieves the long value at the specified position.
    - **Parameters:**
        - `pos`: The position in the array as an `XElements` object.
    - **Returns:** The long value at the specified position.

- `set(pos: XElements, value: Long)`
    - Sets the long value at the specified position.
    - **Parameters:**
        - `pos`: The position in the array as an `XElements` object.
        - `value`: The long value to set.

- `flush()`
    - Forces any changes made to the buffer to be written to the file.

- `close()`
    - Closes the file channel, releasing any resources associated with it.

- `append(value: Long)`
    - Appends a long value to the end of the file.
    - **Parameters:**
        - `value`: The long value to append.

- `allocate(newLength: XElements)`
    - Allocates space for a new length of long elements, extending the file if necessary.
    - **Parameters:**
        - `newLength`: The new length as an `XElements` object.
    - **Throws:**
        - `IllegalArgumentException` if the new length is not greater than the current length.

- `fill(value: Long)`
    - Fills the entire file with the specified long value.
    - **Parameters:**
        - `value`: The long value to fill the file with.

#### Companion Object

- `companion object`
    - Currently, no additional functionality is provided by the companion object.

### Usage Example

```kotlin
val file = File("data.bin")
val longArrayFile = LongArrayMappedFile(file)

longArrayFile.append(123456789L)

val value = longArrayFile.get(XElements(0))

longArrayFile.set(XElements(0), 987654321L)

longArrayFile.flush()

longArrayFile.close()
```

### Notes

- The `LongArrayMappedFile` class is designed for efficient file operations with large datasets, leveraging
  memory-mapped files for performance.
- Ensure that the file is properly closed after operations to avoid resource leaks.
- The `XElements` class is assumed to be a utility class for handling element positions, which should be defined
  elsewhere in the package.

# SequenceFile.kt

## Package: com.simiacryptus.util.files

The `com.simiacryptus.util.files` package provides utilities for handling file operations, specifically focusing on
sequence files that store and retrieve byte arrays efficiently. This package is designed to facilitate the manipulation
of files using memory-mapped I/O for improved performance.

### Class: SequenceFile

The `SequenceFile` class is a utility for managing sequence files, which are files that store sequences of byte arrays.
This class provides methods to append, retrieve, and read byte arrays from a file using memory-mapped I/O for efficient
access.

#### Constructor

- `SequenceFile(file: File)`

  Initializes a new instance of the `SequenceFile` class with the specified file.

    - **Parameters:**
        - `file`: The `File` object representing the file to be used for storing sequences.

#### Properties

- `channel: FileChannel`

  Lazily initialized file channel for reading the file.

- `mappedByteBuffer: MappedByteBuffer`

  Lazily initialized memory-mapped byte buffer for reading the file content.

- `bufferedOutputStream: BufferedOutputStream`

  Lazily initialized buffered output stream for writing to the file.

#### Methods

- `append(str: ByteArray): XElements`

  Appends a byte array to the sequence file.

    - **Parameters:**
        - `str`: The byte array to append.
    - **Returns:** An `XElements` object representing the position of the appended byte array.

- `get(pos: XElements): ByteArray?`

  Retrieves a byte array from the sequence file at the specified position.

    - **Parameters:**
        - `pos`: The `XElements` object representing the position of the byte array to retrieve.
    - **Returns:** The byte array at the specified position, or `null` if the position is invalid.

- `read(): Array<ByteArray>`

  Reads all byte arrays from the sequence file.

    - **Returns:** An array of byte arrays read from the file.

- `getAllIndices(): List<XElements>`

  Retrieves all indices of byte arrays stored in the sequence file.

    - **Returns:** A list of `XElements` objects representing the indices of stored byte arrays.

- `getSize(): Int`

  Gets the number of byte arrays stored in the sequence file.

    - **Returns:** The number of byte arrays.

- `getIndexed(index: Int): ByteArray?`

  Retrieves a byte array from the sequence file at the specified index.

    - **Parameters:**
        - `index`: The index of the byte array to retrieve.
    - **Returns:** The byte array at the specified index, or `null` if the index is invalid.

- `readIndexed(indices: List<Int>): List<ByteArray?>`

  Retrieves byte arrays from the sequence file at the specified indices.

    - **Parameters:**
        - `indices`: A list of indices of the byte arrays to retrieve.
    - **Returns:** A list of byte arrays at the specified indices.

- `close()`

  Closes the sequence file, ensuring that any buffered output is flushed and resources are released.

#### Usage

The `SequenceFile` class is used to manage sequence files efficiently by leveraging memory-mapped I/O. It is suitable
for applications that require fast access to large sequences of byte arrays, such as data processing or storage systems.
The class provides methods to append new data, retrieve specific entries, and read all stored data, making it versatile
for various file manipulation tasks.

# ToBytes.kt

## Files Utility Package Documentation

### Overview

The Files Utility Package provides a set of utility functions and classes designed to facilitate file operations,
particularly focusing on byte and integer array manipulations. This package is part of a larger project aimed at
efficient file handling and indexing.

### Package: `com.simiacryptus.util.files`

#### Function: `Int.toBytes()`

##### Description

The `toBytes` function is an extension function for the `Int` type in Kotlin. It converts an integer into a byte array
representation. This is particularly useful for operations that require data to be in byte format, such as file I/O
operations or network communications.

##### Syntax

```kotlin
fun Int.toBytes(): ByteArray
```

##### Returns

- **ByteArray**: A byte array of length 4, representing the integer in big-endian order.

##### Example Usage

```kotlin
val number = 123456
val byteArray = number.toBytes()

```

##### Implementation Details

- The function creates a `ByteArray` of size 4, which is the standard size for an integer in bytes.
- It uses `ByteBuffer` to wrap the byte array and then puts the integer into the buffer. This ensures that the integer
  is correctly converted into a byte array in big-endian order, which is the default byte order for network protocols.

#### Additional Files in the Package

- **IntArrayAppendFile.kt**: Handles operations related to appending integer arrays to files.
- **IntArrayMappedFile.kt**: Provides functionality for memory-mapped file operations with integer arrays.
- **LongArrayMappedFile.kt**: Similar to `IntArrayMappedFile`, but for long arrays.
- **SequenceFile.kt**: Manages sequence-based file operations, allowing for efficient reading and writing of sequences.
- **XBytes.kt**: Contains utility functions for byte-level operations.
- **XElements.kt**: Provides utilities for element-wise operations on data structures.

#### Usage Context

This package is ideal for applications that require efficient data storage and retrieval, particularly when dealing with
large datasets or when performance is critical. The utilities provided can be used in data processing pipelines, file
indexing systems, and any application that benefits from low-level data manipulation.

#### Dependencies

The package relies on Java's `ByteBuffer` class for byte manipulation, ensuring compatibility and performance across
different platforms.

#### Conclusion

The Files Utility Package offers a robust set of tools for handling file operations at a low level. By providing
functions like `Int.toBytes()`, it simplifies the process of converting and managing data in byte format, making it a
valuable resource for developers working with file systems and data storage solutions.

# ToInt.kt

## Files Utility Package Documentation

### Overview

The Files Utility Package provides a set of utility functions and classes designed to facilitate file manipulation and
data conversion operations. This package is particularly useful for handling byte arrays and mapping files to integer
and long arrays. It includes functionality for converting byte arrays to integers, appending and mapping integer arrays,
and working with sequence files.

### Package Contents

#### 1. `ToInt.kt`

##### Function: `ByteArray.toInt()`

- **Description**: Converts a `ByteArray` to an `Int`.
- **Usage**: This extension function wraps a byte array into a `ByteBuffer` and retrieves the integer representation of
  the byte array.
- **Example**:
  ```kotlin
  val byteArray = byteArrayOf(0x00, 0x00, 0x01, 0x02)
  val intValue = byteArray.toInt()
  println(intValue)

  ```

#### 2. `IntArrayAppendFile.kt`

- **Description**: Provides functionality to append integer arrays to a file.
- **Key Features**:
    - Efficiently appends integer data to a file.
    - Manages file I/O operations for integer arrays.

#### 3. `IntArrayMappedFile.kt`

- **Description**: Maps a file to an integer array, allowing for efficient read and write operations.
- **Key Features**:
    - Memory-mapped file operations for integer arrays.
    - Supports large data sets by leveraging file mapping.

#### 4. `LongArrayMappedFile.kt`

- **Description**: Similar to `IntArrayMappedFile`, but for long arrays.
- **Key Features**:
    - Memory-mapped file operations for long arrays.
    - Efficient handling of large data sets.

#### 5. `SequenceFile.kt`

- **Description**: Manages sequence files, which are files that store sequences of data.
- **Key Features**:
    - Supports reading and writing sequences.
    - Provides utilities for sequence manipulation.

#### 6. `XBytes.kt` and `XElements.kt`

- **Description**: Utility classes for handling byte and element operations.
- **Key Features**:
    - Provides additional byte and element manipulation utilities.
    - Enhances data processing capabilities.

### Usage Examples

#### Converting ByteArray to Int

```kotlin
import com.simiacryptus.util.files.toInt

fun main() {
    val byteArray = byteArrayOf(0x00, 0x00, 0x01, 0x02)
    val intValue = byteArray.toInt()
    println("Converted Int: $intValue")
}
```

#### Appending Integers to a File

```kotlin
import com.simiacryptus.util.files.IntArrayAppendFile

fun appendIntegersToFile(filePath: String, data: IntArray) {
    val file = IntArrayAppendFile(filePath)
    file.append(data)
}
```

#### Mapping a File to an Integer Array

```kotlin
import com.simiacryptus.util.files.IntArrayMappedFile

fun mapFileToIntArray(filePath: String): IntArray {
    val mappedFile = IntArrayMappedFile(filePath)
    return mappedFile.toIntArray()
}
```

### Conclusion

The Files Utility Package provides essential tools for developers working with file-based data storage and manipulation.
By offering efficient methods for converting, appending, and mapping data, this package simplifies complex file
operations and enhances data processing workflows.

# XBytes.kt

## Package: com.simiacryptus.util.files

This package provides utility classes and extensions for handling file-related operations, particularly focusing on byte
and integer manipulations. The primary class in this package is `XBytes`, which offers a convenient way to work with
byte values represented as `Long`.

### Class: XBytes

`XBytes` is a value class that encapsulates a `Long` value, providing a range of operations to manipulate byte data. It
implements the `Comparable` interface, allowing for comparison operations.

#### Properties

- **asLong**: The underlying `Long` value representing the byte data.
- **bytesAsInt**: Returns the `asLong` value as an `Int`.

#### Operators

- **compareTo(other: XBytes)**: Compares this `XBytes` instance with another `XBytes` instance.
- **compareTo(other: Long)**: Compares this `XBytes` instance with a `Long` value.
- **compareTo(other: Int)**: Compares this `XBytes` instance with an `Int` value.
- **plus(other: XBytes)**: Adds another `XBytes` instance to this one and returns a new `XBytes` instance.
- **minus(other: XBytes)**: Subtracts another `XBytes` instance from this one and returns a new `XBytes` instance.
- **plus(other: Long)**: Adds a `Long` value to this `XBytes` instance and returns a new `XBytes` instance.
- **minus(other: Long)**: Subtracts a `Long` value from this `XBytes` instance and returns a new `XBytes` instance.
- **plus(other: Int)**: Adds an `Int` value to this `XBytes` instance and returns a new `XBytes` instance.
- **minus(other: Int)**: Subtracts an `Int` value from this `XBytes` instance and returns a new `XBytes` instance.
- **rem(other: XBytes)**: Computes the remainder of dividing this `XBytes` instance by another `XBytes` instance and
  returns a new `XBytes` instance.
- **rem(other: Long)**: Computes the remainder of dividing this `XBytes` instance by a `Long` value and returns a new
  `XBytes` instance.

#### Infix Functions

- **until(to: XBytes): Iterable<XBytes>**: Creates an iterable range from this `XBytes` instance up to, but not
  including, the specified `XBytes` instance.

#### Extension Properties

- **Int.bytes**: Converts an `Int` to an `XBytes` instance.
- **Long.bytes**: Converts a `Long` to an `XBytes` instance.

This utility class is designed to facilitate operations on byte data, providing a fluent and intuitive API for
developers working with file-related byte manipulations.

# XElements.kt

## Package: com.simiacryptus.util.files

The `com.simiacryptus.util.files` package provides utility classes and extensions for handling file-related operations
with a focus on numerical data manipulation. This package includes classes that facilitate operations on arrays and
elements represented as integers and longs, offering a range of arithmetic and comparison functionalities.

### Class: XElements

The `XElements` class is a value class that encapsulates a `Long` value and provides a range of arithmetic and
comparison operations. It is designed to be used in scenarios where numerical operations on elements are required,
offering a seamless interface for both `Long` and `Int` types.

#### Properties

- **asLong**: `Long`
    - The underlying `Long` value of the `XElements` instance.

- **asInt**: `Int`
    - A computed property that returns the `Long` value as an `Int`.

#### Constructors

- **XElements(asLong: Long)**
    - Initializes a new instance of `XElements` with the specified `Long` value.

#### Methods

- **compareTo(other: XElements): Int**
    - Compares this `XElements` instance with another `XElements` instance based on their `Long` values.

- **compareTo(other: Long): Int**
    - Compares this `XElements` instance with a `Long` value.

- **compareTo(other: Int): Int**
    - Compares this `XElements` instance with an `Int` value.

- **plus(other: XElements): XElements**
    - Adds the `Long` value of another `XElements` instance to this instance and returns a new `XElements`.

- **minus(other: XElements): XElements**
    - Subtracts the `Long` value of another `XElements` instance from this instance and returns a new `XElements`.

- **plus(other: Long): XElements**
    - Adds a `Long` value to this instance and returns a new `XElements`.

- **minus(other: Long): XElements**
    - Subtracts a `Long` value from this instance and returns a new `XElements`.

- **plus(other: Int): XElements**
    - Adds an `Int` value to this instance and returns a new `XElements`.

- **minus(other: Int): XElements**
    - Subtracts an `Int` value from this instance and returns a new `XElements`.

- **rem(other: XElements): XElements**
    - Computes the remainder of the division of this instance by another `XElements` instance and returns a new
      `XElements`.

- **rem(other: Long): XElements**
    - Computes the remainder of the division of this instance by a `Long` value and returns a new `XElements`.

- **rem(other: Int): XElements**
    - Computes the remainder of the division of this instance by an `Int` value and returns a new `XElements`.

- **toInt(): Int**
    - Converts the `Long` value of this instance to an `Int`.

#### Extension Functions

- **until(to: XElements): Iterable<XElements>**
    - Creates an iterable range from this `XElements` instance up to, but not including, the specified `XElements`
      instance.

#### Extension Properties

- **Int.elements: XElements**
    - Converts an `Int` to an `XElements` instance.

- **Long.elements: XElements**
    - Converts a `Long` to an `XElements` instance.

This package is designed to provide a robust set of tools for handling numerical data in file operations, making it
easier to perform arithmetic and comparison tasks with a clean and intuitive API.