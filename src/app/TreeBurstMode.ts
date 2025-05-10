import { createMode } from "../editor/createMode"

export const TREE_BURST_MODE = createMode({
    binaryNumbers: true,
    hexNumbers: true,
    decimalNumbers: "scientific",
    strings: [
        { start: "\"", end: "\"" },
    ],
    indent: ["{", "("],
    dedent: ["}", ")"],
    lineComment: true,
    multilineComment: { start: "/*", end: "*/" },
    tokensBefore: [
        { regex: /(?:\w|\.)*?\/(?:\w|\.)+/, token: "atom" },
        { regex: /\.\w+/, token: "keyword" },
    ],
})
