# CSIR — Canonical Semantic IR (notation reference)

CSIR is a *verification* IR: if a proof obligation could depend on it, it
must be a syntactic node, not a convention (idea.md §5.1). `normalize.op.md`
writes this notation; `extract_obligations.op.md` reads it.

## Unit header

```
unit <qualified::name> (lang=<rust|c|cpp|python|java|go>, profile=<debug|release|...>,
                        int_width=<bits>, rung_hint=<0..5>)
  params: %xs : slice<u32>, %key : u32
  returns: opt<usize>
  effects: read(xs)                 -- effect row of the whole unit
  decreases: <expr | ?>             -- termination slot for recursive units
```

## Blocks and edges

```
^entry:
  ...
  br ^loop
^loop(%lo.1 = φ(%lo.0 @entry, %lo.2 @body)):     -- φ-nodes name all incoming edges
  decreases: %hi.1 - %lo.1                        -- loop variant slot (or `?`)
  invariant: ?                                    -- loop invariant slot
  %c = cmp.lt %lo.1, %hi.1 : bool
  cbr %c, ^body, ^exit
^panic_overflow:  unwind                          -- every trap has an explicit edge
```

## Arithmetic (overflow policy is an operand)

```
%t = arith.add %x, %y : u32 [overflow=trap,  on_trap -> ^panic_overflow]
%t = arith.add %x, %y : u32 [overflow=wrap]
%t = arith.add %x, %y : int                        -- unbounded (Rung 0 only)
%q = arith.div %a, %b : u32 [on_zero -> ^panic_div]
%c = cast %n : usize -> u32 [truncate | trap -> ^panic_cast]
%f = fp.add %a, %b : f64 [rounding=nearest]
```

## Memory and effects

```
%v = mem.load  %p : *u32  region(r1)              effects: read(r1)
     mem.store %p, %v                             effects: write(r1)
%i = slice.index %xs, %k : u32 [bounds=trap -> ^panic_oob]
%n = slice.len %xs : usize
%r = call @f(%a, %b) : T   effects: <summary | ?>  -- external call → framing slot
%h = alloc<T>              effects: alloc [on_fail -> ^oom | infallible]
```

## Concurrency

```
fork ^worker ; join %t
sync.acquire %lock  [order=acquire]   protects(region r2)
atomic.load %p [order=seq_cst]
```

## Recognizer catalogue (idea.md §6.1) — pattern → obligation kind → slot

| CSIR pattern                                  | obligation kind      | slot to fill                     |
|-----------------------------------------------|----------------------|----------------------------------|
| back edge / `decreases: ?`                    | termination          | loop-variant, loop-invariant     |
| self / mutual recursion                       | termination          | termination-measure              |
| `arith.* [overflow=trap]`                     | no-overflow          | precondition (operand range)     |
| `slice.index [bounds=trap]`                   | in-bounds            | precondition (index vs length)   |
| `arith.div/rem`                               | no-trap              | precondition (divisor ≠ 0)       |
| `cast [trap|truncate]`                        | no-trap / spec-fidelity | precondition (value range)    |
| `unwrap` / `expect` / non-null cast           | no-panic             | precondition (definedness)       |
| load after store via distinct pointers        | aliasing             | frame-condition / separation     |
| shared mutable region + `fork`                | data-race-freedom    | environmental-fact (ownership)   |
| nested `sync.acquire`                         | deadlock-freedom     | environmental-fact (lock order)  |
| `fp.cmp`                                      | spec-fidelity        | idealization (tolerance)         |
| `call` with `effects: ?`                      | framing              | frame-condition (callee summary) |
| unbounded input                               | resource-bound       | environmental-fact (size bound)  |
| unit with a name / docstring / test           | functional-spec      | postcondition                    |

Recognizers are deterministic: they decide *coverage*. Agents only fill slots.