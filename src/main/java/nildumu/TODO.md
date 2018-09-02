TODO
----
- fix bugs (test cases)
-- SSA
-- summary approach (but maybe later)
- clean up
-- merge dependencies
-- simplify
- remove topological walks
- quickcheck based program generation for common patterns to test termination
- clean up (merge data and control dependencies, remove old clutter)
- functional approach :D



Old todos
---------




[x] [Value|Bit]::[toString|repr]
[x] input and output bits
- operator
[x] basic specification
[x] unary
[x] binary
[x] phi
[x] less
[x] variable
[x] value 
[x] node to operator
[x] embedd into parser
-- test it
- leakage computation
[x] generate JSON for tool
[x] call tool
[x] small UI that display the result
-- implement Dinics algorithm
[x] … and JUNG view (for faster results) --> till day end
[ ]
- loop stuff
-- merge…
-- fix SSA generation
-- test                  --> till end of tuesday
- clean up code base
- put into own repo
- extended idea          
- tests
-- test framework        --> till end of wednesday


Today
-----
[ ] AST to code
[ ] short descr per AST
[ ] associate values with nodes
[ ] remove old clutter


- implement basic value range implementation (with basic widening and joining → hierarchical clustering)
-- everything other should be the same 

Problem
-------
- applyCondition
-- can it be thrown out?

- loops
-- count loop condition
-- else `while (h == 0) {h++; t = t + 1}` only leaks one bit (with n > 1 wide variables)

- interprocedural approach
-- check for call graph cycles
-- use call string approach
-- if recursion detected: use a basic summary approach
--- call the function with fully unknown parameters with empty deps
--- how does the return value depend on the parameters?
--- iterate (low iteration count)
--- post processing (throw out constant data deps)
--- advantage: no need to reimplement everything to work with abstract values
--- compression idea: just take the distinct paths from each result bit to each input bit
