# Snobol3 Language Implementation in Java

## Snobol3 Language Reference

This section documents the language as implemented in this
interpreter.  Except where noted, it adheres closely to the
language as defined in the Snobol3 Primer [1].  If any reader of
this detects an error in interpretation or in some missing
feature, please contact the author. Since there are many
similarities, knowledge of Snobol4 [2] may be helpful.

### Lexical Structure

Snobol3, like FORTRAN, uses column position to indicate some kinds of
lexical items. In particular, the following characters in column 1 have
the following meaning (using regular expression syntax).
<!-- Use HTML tables because Markdown tables are not standardized (yet) -->
<table>
<tr><th>Character<th>Line Interpretation
<tr><td>[*]<td>Asterisk: rest of the line is a Comment.
<tr><td>[.]<td>Period: this is a continuation of the preceding line.
<tr><td>[a-zA-Z0-9]<td>Label: the line has a label.
<tr><td>[\t ]<td>Whitespace: the line has no label.
<tr><td>[-]<td>Dash: indicates certain flags.
</table>

Note that the set of flags specified in the Primer are useless
in modern computing environments. Instead, this marker has been
modified to allow the insertion of [command line option flags](#option-flags)
into the program. The general form is
"-name[=value]"; the value is optional and if missing is
presumed be the value "true".

The lexical elements are defined as follows (using regular expressions).
<table>
<tr><th>Regular Expression<th>Semantics<th>Comment
<tr><td>[a-zA-Z0-9.]+<td>Name<td>Note that in Snobol3, a string of digits is a name.
<tr><td>[a-zA-Z0-9][a-zA-Z.]*<td>Label<td>Note that labels are a restricted set of names.
<tr><td>[/+-=()*,/$]<td>Delimiters
<tr><td>['][^'\n]*[']<td>String<td>Note the use of single quotes and that string constants may not cross line boundaries. As an extension, [options](#options) exist to allow the use of double quotes and to allow the inclusion of escape sequences (e.g., '\n') in strings.
<tr><td>[\t ]<td>Whitespace<td>This is removed from the token stream before the parser sees it.
</table>

#### Notes:
* Lexically, DIV and SLASH are distinct tokens even though they have the same associated string. Snobol3 parsing requires this in order to disambiguate certain sentences. The distinction made in the lexer is that all arithmetic operators must be surrounded by whitespace. This means that the SLASH token that occurs in a branch must be immediately followed by "s","f", or "(".

* The following names are keywords: "define", "s", "f", "end", "return", and "freturn". It is unclear if these are reserved words, but since it simplifies the grammar, they are treated as reserved in this interpreter.

* In snobol3, many of the symbols are in upper case, except where noted, this interpreter equates lower case and upper case for keywords or built-in function names. Names and labels are still case sensitive. 

### Grammar

The following grammar defines the language relative to the lexical
elements described above. with obvious name changes (e.g. the delimiter
'=' is called EQUAL). EOF of course stands for end-of-file, and EOL
stands for end-of-line.

#### Grammar Specification
````
program: (statement)+ EOF;
statement:   (LABEL)? (body)?  EOL;
body: subject (pattern)? (replacement)? (branch)?;
subject: primary;
replacement: EQUAL (concat)?;

concat: (expr)+;
expr: term ((PLUS|MINUS) term)*;
term: primary ((MULT|DIV) primary)*;
primary: atom | LPAREN unary RPAREN ;
unary: concat | (PLUS|MINUS) concat ;
atom: var | STRING | fcncall;

var: reference | NAME;
reference: DOLLAR primary;

fcncall: FCNNAME args ;
args: LPAREN (arglist)? RPAREN ;
arglist: concat (COMMA concat)* ;

pattern: (pattest)+;
pattest: patvar | expr;
patvar:   STAR patmatch STAR ;
patmatch:   (var)? (SLASH primary | BAR patfcn)? | LPAREN (var)? RPAREN ;
patfcn: PATFCNNAME args ;

branch: SLASH (s (f)? | f (s)? | go);

s: SUCCESS LPAREN dest RPAREN ;
f: FAIL LPAREN dest RPAREN ;
go: LPAREN dest RPAREN ;
dest: label| RETURN | FRETURN | END | reference;
label: LABEL | NAME;
````

This grammar can be processed by Antlr, although Antlr was not used
throughout because of the problem of building the lexer and because it
was overkill for such a simple language. In the rest of this document,
a reference of the form "<...>" generally refers to a grammar non-terminal from the grammar above.

### Processing Cycle
Snobol3 is line oriented. Each line consists of four parts (excluding
the label) (see [Grammar](#grammar-specification)).
1. *Subject*
2. *Pattern*
3. *Replacement*
4. *Branch* 

Many of the parts are optional in some circumstances.

#### Subject

A subject is an &lt;expression&gt; that is computed to produce a string or a
variable reference. It is the target of both pattern matching and
replacement.

As shown in the grammar, an expression is composed of the following
operators: addition (+), subtraction (-), multiplication (*), and
integer division (/). Concatenation is also allowed and is represented
by whitespace between expressions.

Add, subtract, multiply, and divide have the usual precedence. Concat
has a lower precedence than any of the arithmetic operators. Parentheses
are available to alter the evaluation order in the usual way.

Unary minus and plus are allowed, but only immediately after a left
parenthesis; this again avoids an ambiguity in the grammar. Unary
operators may be an extension to Snobol3; the primer is unclear.

The atomic elements of an expression are string constants, variable
references, and function calls. Note that for function calls, it is
illegal to provide too many arguments, but it is ok to provide too few;
the remaining arguments are padded with the empty string.

In order to remove any ambiguity, a subject expression must be enclosed
in parentheses, unless it is an atom.

##### Variables
Variables are atomic elements in an expression
(along with string constants and function calls). Scoping in Snobol3 is
two-level: global and local (to function bodies). This is similar to C.

Variables are specified in one of two ways in Snobol3.
1. Name: an occurrence of a name in an expression is presumed to represent the name of a variable.
2. $(&lt;expression&gt;): a dollar sign followed immediately by a parenthesized expression is interpreted as a dynamically specified variable. That is, the expression is computed and converted to a string. That string is then treated as a variable name. Note that characters that normally cannot appear in a name are allowed here. 

#### Pattern Matching

The basic pattern matching process is as follows.
1. The subject string is computed and an associated cursor is set to 0. The cursor points to the next char to be scanned and ranges from 0 to subject.length().
2. Each pattern element examines the subject string starting at the cursor. If there is a match, the pattern saves information about the substring it matches and moves the cursor to just past that substring.
3. If a pattern element fails, then it invokes a /retry/ on the previous element.
4. On retry, the pattern element attempts to extend its match to include additional characters; it succeeds, then it invokes the following element to match at the new cursor location.
5. If all elements fail, then the initial cursor is moved ahead one character and the match moves forward from that point. If anchoring is in effect, then the initial cursor is not moved and the whole pattern fails. If the cursor moves past the end of the subject string, then the whole pattern fails. 

##### Pattern Matching Elements

Initial: Succeed matching zero characters. If var is specified, then
assign the current matched substring to var.

Retry: if the cursor is at the end of the subject string, fail.
Otherwise, extend the match by one char and succeed. If var is
specified, then assign the current matched substring to var.

<table>
<tr><th>Name<th>Syntax<th>Action
<tr><td>StringMatch<td>&lt;expression&gt;
    <td>Initial: Succeed if the substring at the cursor matches the
    specified string that is the result of computing the
    expression; fail otherwise.
    <td>Retry: fail always.
<tr><td>Arb<td>&lt;var&gt;
    <td>Initial: ?
    <td>Retry: ?
<tr><td>Len<td>&lt;var&gt;/&lt;expr&gt;
    <td>Initial: Compute &lt;expr&gt; as an integer, call it length. It
there are at least length characters following the cursor, then
match those characters and succeed, otherwise fail. If var is
specified, then assign the current matched substring to var.
    <td>Retry: fail always.
<tr><td>Bal(anced)<td>(&lt;var&gt;)
    <td>Initial: Examine the character under the cursor, and act as follows.
	<table>
	<tr><th>Character<th>Action
        <tr><td>[(]<td>Scan ahead to find the shortest matching substring
such that the number of parentheses is balanced. If the end of
the subject string is reached without balance, fail, otherwise
succeed and match the balanced substring.
        <tr><td>[)]<td>Always fail.  
        <tr><td>[^()]<td>Match the character and succeed.
	</table>
If var is specified, then assign the current matched substring
to var.
     <td>Retry: Same as initial. If var is specified, then assign the current
matched substring to var.
<tr><td>Various<td>&lt;var&gt;&nbsp;|&nbsp;&lt;fcncall&gt;
    <td>Initial: Compute the arguments to the
function and invoke the "initial()" entry for the [function](). On success, and if the var is specified, then assign the current matched substring to var.
    <td>Retry: Invoke the "retry()" entry for the function. On success, and if
the var is specified, then assign the current matched substring to var.
</table>

#### Replacement

If the subject evaluates to a variable then all or part of its
current value can be replaced by a string of characters. If
there is no &lt;pattern&gt; specified in the statement, then a
replacement will replace the entire contents of the variable
with the value of the replacement expression. Note that a
missing replacement expression is treated as equivalent to the
empty string; e.g.,
````
    = ''
````
If a &lt;pattern&gt; is specified in the statement, then if the match
succeeds, then the substring of the subject that is matched by the
pattern is replaced by the value of the replacement expression.

#### Branching

Any function call evaluation or pattern match may return a condition
code of success or failure. In the event that a failure is signaled,
then the processing of the rest of the statement is terminated, and
execution of the statement branch part occurs. If the branch specifies a
failure case of (e.g.) the form
````
    f(label1)
````
then transfer of control goes to the statement labeled "label1". If
success is signaled, then execution of the statement continues until the
branch part is reached. If a success case is signaled (e.g.)
````
    s(label1)
````
then transfer of control goes to the label specified by the success
case. It is also possible to specify an unconditional branch
````
    (label1)
````
that will be executed no matter what value of the condition code.

It is unclear what happens if the evaluation of an expression fails and
there is no f() branch specified; does it continue to execute, or does
it stop the stmt execution and move to the next statement? This
interpreter assumes the latter action.

It is also unclear exactly what combinations of branches are allowed.
This interpreter assumes the following combinations are allowed.

  * A success branch followed by an optional failure branch,
  * A failure branch followed by an optional success branch, or
  * An unconditional branch only. 

### Built-In Functions

In the following definitions, arguments "s" and "t", and "u" refer to
string values and "i" and "j" refer to integer valued arguments. Note
that integers are actually encoded as strings since an integer outside
of string would be interpreted as a name.

<table>
<tr><th>Signature<th>Actions
<tr><td>EQUALS(s,t)<td>Succeeds if s.equals(t) (in the Java sense); fails otherwise
<tr><td>UNEQL(s,t)<td>Succeeds if !s.equals(t); fails otherwise.
<tr><td>SIZE(s)<td>returns s.length(); always succeeds.
<tr><td>EQ(i,j)<td>Succeeds if i == j (as integers); fails otherwise.
<tr><td>GE(i,j)<td>Succeeds if i &gt;= j (as integers); fails otherwise.
<tr><td>GT(i,j)<td>Succeeds if i &gt; j (as integers); fails otherwise.
<tr><td>LE(i,j)<td>Succeeds if i &lt;= j (as integers); fails otherwise.
<tr><td>LT(i,j)<td>Succeeds if i &lt; j (as integers); fails otherwise.
<tr><td>NE(i,j)<td>Succeeds if i != j (as integers); fails otherwise.
<tr><td>NUM(s)<td>Succeeds if Integer.decode(s) succeeds ; fails otherwise.
<tr><td>REMDR(i,j)<td>Computes i % j (the remainder function); always succeeds; division by zero causes interpreter to halt.
<tr><td>ANCHOR()<td>Causes the current pattern match to operate in anchored mode; always succeeds; usually the first element in a pattern.
<tr><td>UNANCH()<td>Causes the current pattern match to operate in unanchored mode; always succeeds; usually the first element in a pattern.
<tr><td>PRINT(s)<td>Cause the variable $(s) to operate in output mode.
<tr><td>READ(s)<td>Cause the variable $(s) to operate in input mode.
<tr><td>MODE(s)<td>The argument string s specifies a series of one or more comma
separated flags that affect the global operation of the interpreter. The
currently defined mode flags are as follows.
<table>
<tr><th>Flag<th>Action
<tr><td>anchor<td>Globally set anchored mode to true as the default.
<tr><td>unanchor<td>Globally set anchored mode to false as the default.	
<tr><td>dump<td>Dump the value of all defined global variables at the end of
the program's execution.
<tr><td>dumper<td>Dump the value of all defined global variables at the end of
the program's execution if the program has terminated with an error.
</table>
Mode flags can be set either in the program using the MODE function or
using the "-mode" option flag on the command line. The
expression "MODE('x,y')" is equivalent to specifying the option flag on
the command line (e.g., -mode "x,y").
<tr><td>TRACE(s)<td>The argument string s specifies a series of one or more comma
separated function names. After the execution of this function, the
calls to this function and the return value will be traced and printed
to stdout.
<tr><td>STRACE(s)<td>The argument string s specifies a series of one or more comma
separated variable names. After the execution of this function, all
assignments to any of these variables will be traced and printed to
stderr. Note that the primer indicates that the argument, s, can only
specify one variable name;
it has been extended here to support multiple
names.
<tr><td>CALL(s)<td>The argument string s specifies a function call. It is
dynamically interpreted and the corresponding function is invoked as if
it occurred at the point of the CALL function.
<tr><td>DEFINE(s,t,u...)<td>The argument string s specifies the definition of a
function. The t argument specifies a label that represents the first
statement in the function body. The sequence of zero or more u arguments
represent the names of variables that should be given local scope when
the function is executed.
</table>

### User Defined Functions
Snobol3 has the same, rather baroque, method for defining functions as
does Snobol4.

A function is defined using the "DEFINE" built-in function. This is
treated in a non-standard way in that it is actually evaluated at
compile time, and hence its arguments can only be constant strings. This
also means that it cannot be invoked dynamically. The reasons for this
is that it makes code generation overly difficult and that it seems to
have no use.

The DEFINE "declaration" must precede the label which marks the function
body. This means as a rule that you will have a sequence of defines
interspersed with or preceding a sequence of function bodies.
Appropriate unconditional branches must be associated with one or more
defines to jump around the function bodies.

The general form of a function declaration is
````
    DEFINE(s,t,u...) 
````
where the string s specifies the definition of a function. The t
argument specifies a label that represents the first statement in the
function body. The sequence of zero or more u arguments represent the
names of variables that should be given local scope when the function is
executed.

Syntactically, the function definition (string s) and the locals list
(strings u...) follow this grammar.
````
fcndecl:  NAME LPAREN (formals)? RPAREN;
formals:  namelist;
locals:   namelist;
namelist: NAME (COMMA (NAME)?)*;
````
Invoking a function causes its actual arguments to be evaluated and
assigned to the corresponding formal arguments. The formals arguments
are treated as variables with local scope. The list of locals are
initialized to the empty string and, of course, also have local scope.
Finally, functions return values by assigning to a variable with the
same name as the function. This pseudo-variable also has local scope.

### IO
Snobol3 input/output is basically line-oriented. The language does not
have specific IO functions, but rather associates IO with specific
variables. For example, every time a value is assigned to the variable
SYSPOT, that value is printed to the standard output as a single line.
Similarly, every time the value of SYSPPT is obtained, it reads and
returns a line of text from standard input (minus any trailing newline).

The variables SYSPOT and SYSPPT are predefined. The language has
been extended to also predefine the variables "stdin" and "stdout" as
additional input and output variables respectively.

It is possible to define additional input and output variables using the
primitive functions "READ(&lt;var&gt;)" and "PRINT(&lt;var&gt;)" respectively. The
PRINT function has been extended to take a second string argument. The
second argument is a comma separated list of flags. Currently the
following flags are defined.
* nonewline - A newline is /not/ automatically added to the line as printed.
* stderr - Cause this variable's output to go to the standard error stream instead of standard output. The variable "stderr" is predefined as such a variable. 

## Usage

The interpreter is setup as a Java jar file named "s3.jar", with
_jsnobol3.Snobol3_ as the main class (defined in the jar manifest file).
Invocation can either be the usual form for executing jar files or a
direct invocation of the main class.
````
    java -jar s3.jar [&lt;options&gt;] &lt;program&gt;
    or
    java -classpath s3.jar jsnobol3.Snobol3 [&lt;options&gt;] &lt;program&gt; 
````

### Options
The possible option flags are as follows. Boolean options (such as
-exec) may be preceded by "no" (e.g., "-noexec") to negate the flag.
<table>
<tr><th>Option<th>Description
<tr><td>-envfile &lt;filename&gt;
    <td>Specifies a file from which to read additional options. 
<tr><td>-debug
    <td>Provide some levels of debug output. 
<tr><td>-stacktrace
    <td>Dump a Java stacktrace on fatal errors. 
<tr><td>-exec
    <td>Cause the compiled program to execute. The "-noexec" flag will prevent execution. 
<tr><td>-mode
    <td>Equivalent to including a "MODE()" function call at the beginning of
    the program. 
<tr><td>-dquotes
    <td>Allow programs to use double quotes as well as single quotes. 
<tr><td>-escapes
    <td>Allow string constants to include standard escape sequences such as '\n' or '\u00ff'. 
<tr><td>-lint
    <td>Provide some extra checking to detect such things as potential fall thru into a function body. 
</table>

## Examples

### Absolute Value
````
        define('abs(abs)','abs') /(main)
abs         abs '-' = /(return)
main    stdout = abs('-5')
        stdout = abs('5')
````

### Greatest Common Divisor
````
        define('gcd(m,n)','gcd') /(main)
gcd         gcd = .ne(m) m /f(return)
            m = .remdr(n,m)
            n = gcd /(gcd)
main    stdout = gcd('12','18')
````

### Recursive Factorial
````
        define('rfact(n)','rfact') /(main)
rfact       rfact = .le(n,'1') '1' /s(return)
            rfact = n * rfact(n - '1') /(return)
main    stdout = rfact('4')
````

### Iterative Factorial
````
        define('fact(n)','fact') /(main)
fact        fact = '1'
fact2	    fact = .gt(n) fact * n /f(return)
	    n = n - '1' /(fact2)
main    stdout = fact('4')
````

## Compiler/Interpreter

### Architecture

The interpreter/compiler operates in three passes.

#### Pass1: Lexical Cleanup

The first pass reads in the whole program and processes each line based
on the character in the first column.
<table>
<tr><th>Column 1 Character<th>Action
<tr><td>"*"<td>This signals a comment; remove it.
<tr><td>"."<td>This signals a continuation; combine with previous line to form one single line.
<tr><td>"-"<td>This signals an option of form "name[=value]"; save in the options map and otherwise treat the line like a comment. The possible values are
the same as for command line options.
<tr><td>Label-Character<td>This signals that the line has a label; collect it and store in the "labels" table, otherwise do nothing.
<tr><td>Whitespace<td>This signals that the line has no label; do nothing.
</table>

#### Pass2: Parsing
The second pass applies the grammar to parse the sequence
of statements as constructed during pass1. The output from the parse is
an abstract syntax tree (AST).

#### Pass3: Translation to Pseudo-Code
In pass 3, the AST is walked and a sequence of operators is constructed.
The result is an array of Operator objects. This is passed to the
virtual Machine (VM) for execution.

Pass 3 actually has a second sub-pass to handle forward referencing of
labels. A binding list is kept that lists the operators that need to be
bound to a specific address for a specific named label. This list is
walked after all other code generation. Each specified operator is
modified to refer the to address of the specified label.

The "grammar" for the AST is as follows.
````
program: PROGRAM (statement)*;
statement: STATEMENT LINENO (LABEL)? subj=(primary)? (pattern)? repl=(concat)? (branch)?;
concat: expr | CONCAT (expr)+;
expr: term | ADD term term | SUBSTRACT term term;
term: primary | MULT primary primary | DIV primary primary;
var: NAME String | reference;
reference: REFERENCE primary;
primary: unary | atom ;
unary: concat | NEGATE concat;
atom: STRING | var | fcncall;
fcncall: FCNCALL String arglist;
arglist: ARGLIST (concat)*;
pattern: PATTERN (pattest)*;
pattest: patmatch | expr;
patmatch: BALANCE (var)? | LEN (var)? primary | PATFCN (var)? fcncall | ARB (var)?;
branch: BRANCH dest dest dest;
dest: label | reference;
fcndel: DEFINITION String namelist;
namelist: NAMELIST (NAME)*;
LABEL: [String];
NAME: [String] ;
FCNCALL: [String];
STRING: [String];
LINENO: [Integer];
````
The right side describes the structure of an AST node. The names in
capitals represent the type of the node. The subtrees, if any, are
defined by the structures that follow the type. Thus, a "branch" AST has
the type BRANCH and has three subtrees that have the structure defined
by "dest".

Right sides that consist of a single bracketed type are leaves and have
an associated value of that Java type. Thus, a LABEL is a leaf with the
label name encoded as a Java String object.

#### Execution
The virtual machine interpreter, VM (in file "VM.java"), is more-or-less
a standard stack machine. It has a program counter (pc) that specifies
the next operator to execute. Its main loop reads the operator at
code[pc], increments pc, and then invokes the execute() method of the
operator. It continues until the End operator is executed or a fatal
error occurs.

To execute, the operator obtains its arguments, if any, and then
performs its specific action. This action typically will pop its
arguments, perform some computation, and push a result onto the stack.

An Operator can obtain arguments in two ways. First, it can access the
stack using push(),pop(), and top(). Second, it can have a constant
argument built into the Operator instance.

#### Virtual Machine Operators
In the following, constant arguments are indicated in square brackets
and stack arguments are indicated in parentheses, with the topmost on
the stack argument being the rightmost in the parentheses. The first
list contains all non-pattern operators, which are defined in
"Operator.java". The second list contains pattern related operators,
which are defined in "PatternOp.java".

<table>
<tr><th>Operators<th>Semantics
<tr><td>Add(a,b)<td>a = a + b
<tr><td>AssignLocal(value,var)<td>Assign the value to a local variable.
<tr><td>Assign(value,var)<td>Assign the value to a variable; If the variable is
defined locally or globally, then assign to that variable, otherwise
create locally if possible, globally otherwise.
<tr><td>Begin<td>Do any necessary initialization at the start of the whole program.
<tr><td>BeginFcn<td>Create the new local space for this invocation of the function.
<tr><td>BeginStmt[dest,lineno]<td>Mark the stack for use by EndStmt, initialize
the condition code, and save dest as a pointer to the end of the
statement. Also, set the source line number for the statement.
<tr><td>CVI<td>Convert the top stack value to an Integer.
<tr><td>CVS<td>Convert the top stack value to a String.
<tr><td>CVV<td>Convert the top stack value to a variable (the "$" operator).
<tr><td>CallRet<td>Used by the CALL statement to cause a call to return after the
CALL statement.
<tr><td>Concat(a,b...)[N]<td>Concatenate the top N elements into a single string.
<tr><td>Deref(var)<td>Replace the var with its value on the stack.
<tr><td>Div(a,b)<td>a = a / b; using integer division
<tr><td>Dup(a)<td>Push a copy of a onto the stack.
<tr><td>End<td>Signal the virtual machine to stop executing.
<tr><td>EndStmt[sdest,fdest]<td>Clear the stack down to the point marked by the
previous BeginStmt. Use the condition code to start execution of the
success or failure branches.
<tr><td>Frame<td>Save the current frame and create a new one on entry to a function.
<tr><td>FReturn<td>Set the condition code to failure and return to just after the
call.
<tr><td>IJump(var)<td>Pop the var, get its value, and use it as a label name to
which to jump.
<tr><td>JSR[dest]<td>Save the pc into the VM's frame and jump to the specified
destination. This is effectively the call instruction for invoking a
function.
<tr><td>Jump[dest]<td>Jump to dest.
<tr><td>Mult(a,b)<td>a = a * b
<tr><td>Negate(a)<td>a = - a
<tr><td>Nth<td>Extract the nth element in the eval stack, with n=0 as the stack top.
<tr><td>Primitive[primfcn]<td>Invoke the specified primitive function.
<tr><td>Push[value]<td>Push value onto the eval stack.
<tr><td>Replace(var,range,rhs)<td>Replace the substring of the var as specified by
the range with the rhs value. Pop all three from stack.
<tr><td>RetVal<td>Extract the return value for a user defined function and load it
into the return value in the VM. Recall that the return value for a user
defined function is the variable with the same name as the function.
<tr><td>Return<td>Push the VM's current return value onto the eval stack.
<tr><td>Subtract(a,b)<td>a = a - b
<tr><td>Swap(a,b)<td>tmp=a; a=b; b=tmp; (Swap top two eval stack elements).
<tr><td>Unframe<td>Throw away the current frame and return to the previous frame.
<tr><td>Used<td>as part of the function return process.
</table>

<table>
<tr><th>Pattern-Related Operators<th>Semantics
<tr><td>Arb<td>Implement the Arb pattern element.
<tr><td>Balance<td>Implement the Balanced pattern element.
<tr><td>BeginPattern(subject,var)<td>Load the subject string into the VM's match
state. If the subject is also a variable, then load that as well. On
retry, move the anchor point if allowed, otherwise fail.
<tr><td>EndPattern(range...)<td>Successful conclusion to a match, so capture the
full range matched by the pattern, remove all interim ranges, and leave
the full range on the stack.
<tr><td>Len<td>Implement the Len pattern element.
<tr><td>StringMatch<td>Implement the StringMatch pattern element.
</table>

As an aside, it would be interesting to see if this set could be converted to use the Snobol4 macro operators.

### Extending the Interpreter

#### Adding a New Primitive Function
Each primitive function definition is encapsulated as a class that is a
subclass of the class "Primitive". Let us define a new function called
"rematch(s,re)". This function fails if the regular expression _re_ does
not match the target string _s_. It succeeds otherwise.

To define the function, we add the following code to the source file
_Primitive.java_.
````
class $REmatch // The primitive class names begin with $ as a convention.
      extends Primitive
{
    public $REmatch() {super(2);} // This function &lt;= 2 arguments
    public ArgType typeFor(int argi)
	{return ArgType.CVS;} // All arguments are required to be a string
			      // The compiler will insert appropriate CVS
			      // operators to ensure this.
    public void execute(VM vm, PrimFunction fcn) throws Failure
    {
	String re = (String)vm.pop(); // pop regular expression off the stack.
	String s = (String)vm.pop(); // pop target string off the stack.
	if(re.length() == 0)
	    throw new Failure(vm,"rematch: empty regular expression");
	// See if the string can be matched by the RE
	boolean match = false;
	try {
	    match = s.matches(re);
	} catch (PatternSyntaxException pse) {
	    throw new Failure(vm,"rematch: illegal regular expression: "+re);
	}
	vm.cc = !match; // if the match failed, then set the condition code.
	setReturn(); // return empty string on success
    }
}
````

This function is made known to the interpreter by adding the following
line to the procedure "definePrimitives()" at the top of the file
"Primitive.java".
````
        fcnDef("rematch",(p=new $REMatch()));
````

#### Adding a New Pattern Element
Adding a new pattern element is somewhat more complicated than adding an
operator because it must support both initial and retry cases. It also
requires an extension to the syntax of Snobol3 so that new pattern
operators can be specified. The syntax chosen is patterned on the following form.
````
    <var>|<fcncall>
````
The example below defines a variant of the balanced pattern. It takes
one argument: a string of length two and specifying the left and right
brackets to match. Thus, the operator _(x)_ would be equivalent to
_x|bracket('()'))_.

To define the function, we add the following code to the source file
"PatternOp.java".
````
class BracketOp extends PatternOp
{
    char lbracket = 0;
    char rbracket = 0;

    public BracketOp() {super();}
    public int nargs() {return 1;}

    // On entry, the stack from top down contains the following items:
    // top: function argument n 
    //      function argument n-1 
    // ...
    //      function argument 1
    //      var name or null if none specified

    public void initial() throws Failure
    {
	// get the bracket pair
	String pair = (String)vm.pop();
	// get name of our variable from stack and keep until failure
	opvar = (VRef)vm.pop();
	if(pair.length() != 2)
	    throw new Failure(vm,"Bracket: illegal bracket pair: "+pair);
	// decompose the bracket pair
	lbracket = pair.charAt(0);
	rbracket = pair.charAt(1);
	// create an initial range value
	Range r = new Range(state.cursor,state.cursor);
	if(!bracket(r)) {
	    fail(r.r0); // fail and revert the cursor
	} else {
	    assign(r);  // assign the range to the variable, if any
	    succeed(r); // succeed, saving the current range on the stack
	}
    }

    public void retry() throws Failure
    {
	Range r = (Range)vm.pop(); // obtain the range from the last retry
	if(!bracket(r)) fail(r.r0); else {assign(r); succeed(r);}
    }

    boolean bracket(Range r)
    {
	String subject = state.subject; // get the subject string
	int len = subject.length(); // and length
	if(r.rn &gt;= len) return false; // if we have matched to the end,
				      // we cannot extend, so signal failure
	char ch = subject.charAt(r.rn);
	if(ch == rbracket)
	    return false; // if the next character is the rbracket, fail
	if(ch != lbracket) {
	    r.rn++;   // if next character is not an lbracket, advance one char
	    return true; // and succeed
	}
	// Only case left is that we are at an lbracket, need to scan
	// forward to find a matching rbracket, taking nesting into account
	int rn = r.rn+1;
	int depth = 1;
	while(rn &lt; len && depth &gt; 0) {
	    ch = subject.charAt(rn++);
	    if(ch == lbracket)
		depth++;
	    else if(ch == rbracket)
		depth--;
	    // else do nothing
	}
	if(depth == 0) {
	    r.rn = rn; // record our current last matched char + 1.
	    return true;
	}
	// we got to the end of the subject without balancing the brackets
	return false;
    }
}
````
This function is made known to the interpreter by adding the following
line to the procedure "definePatterns()" at the top of the file
"PatternOp.java".
````
    Snobol3.patterns.put("bracket",new BracketOp());
````

There are some things to note.
1. The Range class specifies the range of characters in the subject
string currently matched by the given pattern. The range is like
String.substring arguments, which means that the first index points
to the first matched character and the last index (rn) points to the
character just after the match. 

## Installation
The distribution contains both an ant build.xml file and a Makefile.
Both have the following major tasks defined.
* _all_: Compile the source and construct the jar file. It is assumed that the JDK 1.5 bin directory is in the PATH environment variable.
* _clean_: Delete the .class files, the jar file, and the manifest file.
* _tests_: Execute all of the test programs.
* _examples_: Execute all of the example programs. 

## Change Log
Minor version levels are indicated in parentheses.

### Version 1.
* (0) This is the initial release 

## References
[1] "Snobol 3 Primer: an introduction to the computer programming language." by Allen Forte. M.1.T. Press, 1967.

[2] "The SNOBOL 4 programming language" (2nd Ed.) by R. E. Griswold, J. F. Poage, and I. P. Polonsky. Prentice-Hall, 1971. 

## License

### BSD 3-Clause License

#### Copyright (c) 2023, Dennis Heimbigner

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Point of Contact {#nczarr_poc}

__Author__: Dennis Heimbigner<br>
__Email__: dennis.heimbigner@gmail.edu<br>
__Initial Version__: 10/1/2005<br>
__Last Revised__: 12/18/2023<br>
__Latest Version__: Snobol3 Version 1.0<br>
__Minimum JDK Level__: JDK 1.5<br>

## A Personal Note:

Snobol3 has always held a special fascination for me. It was the
first language I learned (after FORTRAN), and it was the first
language for which I built a partial interpreter for the IBM
1620 computer in about 1969. I recently (2005) found my old copy
of the Snobol 3 Primer, and since I had some time on my hands, I
decided write an interpreter for it in Java.
