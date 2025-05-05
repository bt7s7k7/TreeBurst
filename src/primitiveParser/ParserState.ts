declare const _PARSER_STATE: unique symbol

export class ParserState<T> {
    declare protected readonly [_PARSER_STATE]: T

    constructor(
        public readonly label: string,
        public readonly defaultValue: T,
    ) { }
}
