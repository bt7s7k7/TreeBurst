import { AbstractConstructor } from "../../comTypes/types"

export function recordClass<TCtor extends (...args: any) => Record<string, any>>(base: undefined, ctor: TCtor): { new(...args: Parameters<TCtor>): ReturnType<TCtor> }
export function recordClass<TBase extends AbstractConstructor, TCtor extends (...args: any) => Record<string, any>>(base: TBase, ctor: TCtor): { new(...args: [...ConstructorParameters<TBase>, ...Parameters<TCtor>]): ReturnType<TCtor> & InstanceType<TBase> }
export function recordClass(base: AbstractConstructor | undefined, ctor: (...args: any[]) => any): AbstractConstructor {
    return class extends (base ?? Object) {
        constructor(...args: any[]) {
            super(...args.slice(0, args.length - ctor.length))
            Object.assign(this, ctor(...args.slice(args.length - ctor.length)))
        }
    }
}
