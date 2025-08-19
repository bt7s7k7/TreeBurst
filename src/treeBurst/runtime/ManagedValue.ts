import { VOID } from "./GlobalScope"
import { ManagedArray } from "./ManagedArray"
import { ManagedFunction } from "./ManagedFunction"
import { ManagedTable } from "./ManagedTable"
import { UnmanagedHandle } from "./UnmanagedHandle"

export type ManagedValue =
    | string | number | boolean
    | ManagedTable
    | ManagedFunction
    | ManagedArray
    | null
    | typeof VOID
    | UnmanagedHandle
