# ![TreeBurst Icon](./packages/treeBurstJava/docs/TreeBurst.png) TreeBurst

TreeBurst is a small, dynamically typed, embeddable programming language. Currently very work in progress. [Documentation is available here](https://bt7s7k7.github.io/TreeBurst/).

Features:

  - **Prototype based inheritance** inspired by JavaScript and Lua
  - **Functional features** such as the pipeline operator and partial application
  - **Metaprogramming** by AST modification

```js
[header, ...contentItems, footer]
	.filter(\?.size < 10)
	.map(\(item) Button.new({
		label: item.name
		callback: \item.value += 10
	}))
	|> Document.append(...?)
```
