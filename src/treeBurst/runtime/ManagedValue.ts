import { VOID } from "./GlobalScope"
import { ManagedFunction } from "./ManagedFunction"
import { ManagedTable } from "./ManagedTable"

export type ManagedValue =
    | string | number | boolean
    | ManagedTable
    | ManagedFunction
    | null
    | typeof VOID
