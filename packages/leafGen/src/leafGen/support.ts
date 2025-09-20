export function matchesGlob(input: string, glob: string) {
    if (glob.startsWith("*") && glob.endsWith("*")) {
        return input.includes(glob)
    } else if (glob.startsWith("*")) {
        return input.endsWith(glob.slice(1))
    } else if (glob.endsWith("*")) {
        return input.startsWith(glob.slice(0, -1))
    } else {
        return input == glob
    }
}
