## Command

```

/**
 * Calculates the sum of a range of integers and prints the result.
 * 
 * @param start The starting integer of the range.
 * @param end The ending integer of the range.
 * @return The total sum.
 */
def calculateRangeSum(int start, int end) {
    def sum = (start..end).sum()
    println "The sum of numbers from $start to $end is: $sum"
    return sum
}

calculateRangeSum(1, 100)

```
## Result
```
5050
```
## Output
```

```
