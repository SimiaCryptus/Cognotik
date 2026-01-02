
### Query 1
```sql
CREATE TABLE test_table (id INTEGER PRIMARY KEY, val VARCHAR(255))
```
Result:
Update Count: 0

### Query 2
```sql
INSERT INTO test_table (id, val) VALUES (1, 'Hello')
```
Result:
Update Count: 1

### Query 3
```sql
INSERT INTO test_table (id, val) VALUES (2, 'World')
```
Result:
Update Count: 1

### Query 4
```sql
SELECT * FROM test_table ORDER BY id ASC
```
Result:
| ID | VAL | 
| --- | --- | 
| 1 | Hello | 
| 2 | World | 

