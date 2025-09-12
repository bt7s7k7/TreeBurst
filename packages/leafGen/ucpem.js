// @ts-check
/// <reference path="./.vscode/config.d.ts" />

const { github, project, run, join, constants, ucpem } = require("ucpem")

project.isChild()

project.use(github("bt7s7k7/Apsides").script("builder"))

project.prefix("src").res("leafGen",
    github("bt7s7k7/MiniML").res("cli"),
    github("bt7s7k7/MiniML").res("miniML"),
)

project.script("leaf-gen", async (args) => {
    await ucpem("run builder build")
    process.argv = [...process.argv.slice(0, 2), ...args]
    await import(join(constants.projectPath, "./build/index.mjs"))
}, { desc: "Generates TreeBurst library documentation", argc: NaN })
