Nildumu
=======

An experimental quantitative information flow analysis on a PDG like
structure for a while-language with functions, one data type (`int`)
and a C like syntax. Example programs can be found in the `examples`
directory.

It is based, and developed alongside, my master thesis.
A working draft can be found at https://git.scc.kit.edu/uqddy/ma.
It uses the *swp* parser and lexer generator for parsing the demo
language. The generator was originally written some years ago, but it
changed slightly during the implementation of this project, as this
project is the first real use of the generator.

Run a basic UI via the `gui` script in the main directory. This allows
to run the analysis and see the computed leakage and the bit dependency
graph.

Language
--------
- the language has a C like syntax
- it supports `if` and `while` statements that only consider the first
bit of the result of their conditional expression
- it supports different widths for the data type `int`, the default
width is gathered by taking the maximum bitwidth of all literals in the
program
    - define the width by using `bit_width N` as the first statement
    in the program, `N` being the desired maximum bit width
- it supports different security lattices, use `use_sec [lattice|basic]`
  to set the used lattice
    - basic lattice: just consists of `h` (high) and `l` (low)
    - diamond lattice: extends the basic lattice by two uncomparable
      middle values `m` and `n`
    - this statement has to come before all other statements (even
      the `bit_width` statement)
- input variables of level `X` can be defined in the global scope by
  using the syntax `X input int VAR_NAME = INPUT_LITERAL`
    - `INPUT_LITERAL` can be a signed integer literal or a binary literal
      (`0b…`) where bits can also be specified as `u` (statically unknown)
    - this the only case where variability can be introduced
    - typically unknown bits of high input variables are considered the
      secret
- output variables of level `X` can be defined in the global scope by
  using the syntax `X output int VAR_NAME = EXPRESSION`
- functions have `int` as a return value, even if they don't return
  anything and can have arbitrary parameters
  - only the last statement of a function can be a return-statement
  - global variables cannot be accessed within a function
  - the order of function definition is not important
- it currently supports the following operators in expressions
  - `&` (`&&` is not implemented, because it typically involves
    short-cutting)
  - `|` (same here)
  - `^` (bit wise xor)
  - `=`, `!=`
  - `<`, `<=`, `=>`, `>`
  - `+`, `-`
  - unary operators: `~`, `-`, `!`
  - `·[n]`: a bit select operator, that selects the `n`th bit, `n` being
    a literal >= 1
  - `[n]·`: a bit place operator, that returns a value that has the
    first bit of the child expression as its `n`th bit and is otherwise
    zero
- the features are trimmed down, to make the implementation as simple
  as feasable

UI
---
The following describes the UI (launch it via `./gui`).
All configurations and inputs are stored continously in the
`gui.config` file, they are brought back after a restart of a program

- the top combobox allows to access different examples from the
  `examples` folder and to store the current program there in,
  using the 🖪 button
- the text input allows to input the program, it supports basic
  auto completions, syntax highlighting, code folding and syntax error
  highlighting
- the checkbox labeled ⮔
    - if checked, the input program is analysed continously
    - only use this for small programs with short analysis times
- the checkbox labeled `+ → &!|^`
    - if checked, all `+` and `-` operators will replaced by their
      respective bit operation networks
    - this yields to a far worse perfomance, but has theorectial
      advantages
    - there is no difference in the leakage computation
- the combobox labeled *Output*
    - `MIN`: only update the *Leakage* table
    - `WONR`: also update the *Variable values* table
    - `ALL`: also update the *Node values* table and output debug
      information on the console
- the button labeled 🢒
    - run the analysis, changes to `…` during the run of the analysis
- the button labeled ⏹
    - abort the current run of the analysis
- the *Mode* combobox
    - *basic*: use the basic version of the analysis that only supports
      if-statement and doesn't take into account that conditions fixate
      specific bits in the then- and the else-branch of if-statements
   - `extended`: takes the last point into account
   - `loop`: full support of the whole language
- the big combobox below
   - it allows to select and configure the method handler the handles
     the method invocations
   - some example configurations are already included
   - it uses as syntax the standard Java property syntax, using `;`
     instead of line breaks
   - handlers are selected using the property `handler`
        - if this misses and the configuration string just consists of
          a single identifier, then this identifier is considered as
          the chosen handler
   - the `basic` handler
        - this is the default handler, that just connects returns
          a return value for every function call in which every bit
          depends on every parameter bit
        - this yields to maximal over-approximation, but it is fast
          and therefore used as default handler to gather some kind of
          basis for other handlers
   - the `call_string` handler
        - A call string based handler that just inlines a function.
        - If a function was inlined in the current call path more
          than a defined number of times (`maxRec`), then another
          handler is used to compute a conservative approximation
        - properties
            - `maxrec`: default is `2`
            - `bot`: the handler for the overapproximation, just
              a handler configuration, as the current one
                - allows to chain handlers, it might be useful to use
                  the `summary` handler here
                - default is `basic`
   - the `summary` handler
        - A summary-edge based handler.
        - It creates for each function beforehand summary edges:
            - these edges connect the parameter bits and the return bits
        - The analysis assumes that all parameter bits might have a
          statically unknown value.
        - The summary-edge analysis builds the summary edges using a
          fix point iteration over the call graph.
        - Each analysis of a method runs the normal analysis of the
          method body and uses the prior summary edges if a method is
          called in the body.
        - The resulting bit graph is then reduced.
        - It supports coinduction (`mode=coind`)
          and induction (`mode=ind`), but the default is to choose
          induction for non-recursive programs and else coinduction
          (`mode=auto`)
        - Induction starts with no edges between parameter bits and
          return bits and iterates till no new connection between a
          return bit and a parameter bit is added.
            - It only works for programs without recursion.
        - Coinduction starts with the an over approximation produced by
          another handler (`bot` property) and iterates at most a
          configurable number of times (`maxiter` property), by default
          this number is 2147483647 (the maximum number of signed 32 bit
          integer)
        - The default reduction policy is to connect all return bits
          with all parameter bits that they depend upon
          ("reduction=basic")
            - An improved version (`reduction=mincut`) includes the
              minimal cut bits of the bit graph from the return to the
              parameter bits, assuming that the return bits have
              infinite weights
        - properties
            - `maxiter`: maximum number of iterations for the
              coinduction, as every intermediate state is also valid
            - `bot`: default is `basic`
            - `mode`: `ind` or `coind`, default is `coind`
            - `reduction`: reduction policy, either `basic` or `mincut`,
              default is `mincut`
            - `dot`: folder to output dot files for the bit graphs of
              the methods in different iterations and the call-graph
                - default: empty string, produces no dot files
            - `csmaxrec`: if > 0, each sub analysis uses a call-string
              based handler for evaluation method invocations, using
              the computed summary edges as `bot` and the passed value
              as `maxrec`. Likely improves precision but also increases
              the size of the summary edges.
- the combobox labeled *Min-Cut* allows to choose between several
  minimum-cut algorithms for the analysis
    - the JUNG version is correct and optimal
    - *approximate Edmonds-Karp* is faster as it doesn't need to
      construct an edge weighted graph, but gives only approximate
      results, but the results are in most cases optimal
- the tabs below
    - *Leakage*: contains the leakage of information from higher or
      non-comparable security levels into each security level
    - *Preprocessed*: contains the SSA resolved version of the program
      with some operators replaced
    - *Output*: Just some statistics
    - *Node values*: the values of the AST/PDG-nodes at the end of the
      analysis
    - *Variable values*: the values of the variables in the program
      at the end of the analysis
- the graph view on the right side
    - it display the bit dependency graph with the green dots being
      the imaginary output (`o`) and imaginary input (`i`) nodes
    - the red dots are the bits of the min-cut (see thesis)
    - clicking on the nodes will highlights the approximate location
      in the program where they originating
- the other controls do exactly what their labels says
    - *reset* resets the zoom level and view

It's licensed under the GPLv3 and MIT license.