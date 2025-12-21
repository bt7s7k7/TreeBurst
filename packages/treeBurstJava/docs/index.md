<div style="text-align: center">
	<img src="./TreeBurst.png" style="image-rendering: pixelated;" alt="TreeBurst brand image" width="100"></img>
	<h1>TreeBurst</h1>
</div>


TreeBurst is a small, dynamically typed, embeddable programming language.

Features:

  - **Prototype based inheritance** inspired by JavaScript and Lua
  - **Functional features** such as the pipeline operator and partial application
  - **Metaprogramming** by AST modification
  - **Pattern matching** (WIP)

```js
[header, ...contentItems, footer]
	.filter(\?.size < 10)
	.map(\(item) Button.new({
		label: item.name
		callback: \item.value += 10
	}))
	|> Document.append(...?)
```

# Guide

## Basic Syntax

Define a variable using the `$` operator.

```js
$val = 5
```

Reference a variable just using its name.

```js
val
```

Comments can be used to annotate code.

```js
// Single line commend
/* Multiline
   comment */
```

The language supports the following primitive values, that can be used by a literal.

```js
null // Specifies an empty value
void // Specifies a missing value, cannot be added to data-structures

// Number literals
5, 5.25, -28

// Boolean literals
true, false
```
There are multiple types of string literals. The only difference is the type of quote. You only need to escape the quote used to surround the string, you can choose a quote that is not contained in your string to avoid the need for escape sequences.

```js
'String'
"Hello\nworld\x00"
`No "escape" needed`
```

There is also a template string literal, which can be used to create a string from other values. This is achieved by prefixing any string literal with `$` and values are included by surrounding them with `${}`.

```cs
$"The sum of ${value1} and ${value2} is ${value1 + value2}"
```

## Expression separation

Expression are separated by the `,` token. However, in many cases this is not required. Expression are considered separate unless there is a syntax ambiguity. Generally, it is recommended to always use `,` in function calls and when there are multiple expression on the same line. Otherwise expression should be separated by newlines.

```js
{a: 1 b: 2 c: 3} // Separation is not required but recommended
{a: 1, b: 2, c: 3} // Correct syntax

{
	a: 1
	b: 2
	c: 3 // Separation by newline
}
```

The following example demonstrates a syntax ambiguity between grouping and function invocation. The parser will understand it as an invocation, which was probably not intended. Also included are 

```js
// Incorrect syntax
(1 + 2) (3 + 4)
// Separation by `,`
(1 + 2), (3 + 4)
// Separation by newline
(1 + 2)
(3 + 4)
```

Binary operators can bind operands over multiple lines, therefore newlines are not always sufficient for separation. The following example demonstrates an ambiguity between a binary subtraction and unary negation. The parser will understand it as a subtraction.

```js
// Incorrect syntax
value
-2
// Explicit separation
value,
-2
// If subtraction was indented, indicate such by a space
value
- 2
```

Function invocations and indexing does not bind over multiple lines. There is no ambiguity in the following examples.

```js
// Invocation
a(5)
// Two separate expression
a
(5)
// Indexing
array[10]
// Two separate expression
array
[10]
```

## Functions

Later references to variables do not use `$`, only their name. Variables are accessible inside of the declaring function or all function declared within.

To create a function, use the `\\` operator. A function can specify explicit parameters and/or can act as a partial application of another operation. Partial application is performed using the placeholder `?`. Each placeholder creates an implicit parameter and is replaced by reference to that parameter.

```js
// Function that returns a constant value
\51

// Two parameter function
\(a, b) a + b

// Equivalent partial application
\? + ?

// If you want to reuse a parameter, placeholders cannot be used
\(a, b) a + b + a

// Function with a block allow for multiple operations.
// In this case placeholder cannot be used.
\(item) {
	item.one()
	item.two()
}
```

Functions implicitly return the result of the contained expression, the {@link return} function can be used to abort execution early with a return value.

```js
\(item) {
	item.name == null && return("Missing name")

	return(item.name.length)
}
```

Function parameters can specify a default value. You can also create a function that takes a variable amount of parameters using a rest parameters, which consume all unbound arguments.

```js
$func = \(a = 1, ...b, c = 2) ({a, b, c})

func() // `a` is `1`, `b` is [], `c` is `2`
func(3, 4) // `a` is `3`, `b` is [], `c` is `4`
func(3, 4, 5, 6) // `a` is `3`, `b` is [4, 5], `c` is `6`
```

The spread operator can be used to provide multiple arguments from one variable.

```js
$func = \(a, b) a + b

$array = [1, 2]
func(...array)
```

## Tables

A {@link Table} object is the simples composite type. It is an object that has multiple properties that can be accessed using the `.` operator. A table is created using the {@link Table.new} function. 

Properties can be declared similarly to variables, using the `$` operator. A property can only be declared once. Duplicate declaration or access to an undeclared property will generate an exception. As with any other composite type, a table cannot contain a {@link void} value.

```js
$table = Table.new()
$table.property = 21

table.property = 21
```

The {@link Table.new} function can take a list or a map of properties to initialize the table. See the linked reference page for details.

A special type of function is a method. This is specified by naming the first parameter `this`. When this function is called by accessing it from a table, the table is inserted as the first argument.

```js
$foo = Table.new()
$foo.method = \(this) this
foo.method() == foo
```

To access a property dynamically you can use the {@link Table.getProperty} function.

```js
$foo = Table.new({ x: 5 })
Table.getProperty(foo, "x") // Returns: 5
Table.getProperty(foo, "y") // Returns: void
```

## Array

An {@link Array} is a composite type that can store multiple value in order. Arrays start at `0` and have a limited length. They cannot be sparse. You can create an array using by surrounding elements between `\[` and `]`. As with any other composite type, an array cannot contain a {@link void} value; therefore all {@link void} elements are ignored during creation.

```js
[1, "value", void, true, null]
// Results in: [1, "value", true, null]
```

You can use the `length` property to get the length of the array.

```js
[1, 2, 3].length // Returns: 3
```

You can use a spread operator to include multiple elements from one variable.

```js
$x = [1, 2]
$y = [true, false]
$z = [...x, ...y, ...x]
// Results in: [1, 2, true, false, 1, 2]
```

Array elements are accessed using the `\[]` operator (see {@link Array.prototype.k_at}). Indexing outside the range of the array generates an exception. You can use a negative index to index from the end of the array.

```js
$x = [1, 2, 3]
x[0] // Returns: 1
x[-1] // Returns: 3
x[-2] // Returns: 2
```

You can use the {@link Array.prototype.tryAt} method to access elements that may be outside of the array range. 

```js
$x = [1, 2]
x.tryGet(0) // Returns: 1
x.tryGet(5) // Returns: void
x.tryGet(5) = 6 // The array is extended to fit the new element
```

For additional functions see the {@link Array} reference.

## Map

A {@link Map} object is a composite type that allows the storage of values indexed by unique keys. Primitive types are compared by value but composite types are compared by reference. 

You can create a map by surrounding the entries between `{` and  `}`. Entries can be simple, indexed by a constant string key or computed, indexed by a value returned from an expression. You can also create an entry from a variable, that will have the key of the name of the variable. As with any other composite type, a map cannot contain a {@link void} value; therefore all {@link void} elements are ignored during creation.

```js
$variable = 28

{ variable, simpleKey: "value", [getComplicatedKey() + 52]: "other" }
```

You can use the `length` property to get the entry count of the map.

```js
{a, b, c}.length // Returns: 3
```

A map can be indexed using the `\[]` operator (see {@link Map.prototype.k_at}).

```js
$map = {}
map["a"] == void

map["a"] = 5 // Creates a new property
map["a"] == 5
map.length == 1

map["a"] = 6 // Modifies an existing property
map["a"] == 6

map["a"] = void // Deletes a property
map["a"] == void
map.length == 0
```

If you only use a constant string key, you can use the direct access operator `->` for a simpler syntax.

```js
map->a
// Equivalent to:
map["a"]
```

## Macros

Macros are special functions prefixed by `@` that execute during bytecode compilation. Instead of operating on values, they take expressions as arguments and emit code.

```js
@while(true) \{
	print("Forever")
}

[1, 2, 3].@foreach \print(?)
```

Bytecode compilation happens the first time a function is called. At this point, the function scope will be used to resolve the macro. If the macro is used as a method, the {@link Table.prototype} object will be used. If the macro is not found, the its execution will be deferred to the point, where the function execution first reaches the macro. This allows to use macros that are methods of custom classes. Next time the function executes, there will be no macro, only the emitted code.

Currently macros can only be created using native code. While you can define a macro from code, there is no API to work with expressions.
