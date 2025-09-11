// @ts-check
/// <reference path="./.vscode/config.d.ts" />

const { github, project } = require("ucpem")

project.isChild()

project.use(github("bt7s7k7/Apsides").script("builder"))

project.prefix("src").res("leafGen",
    github("bt7s7k7/MiniML").res("cli"),
    github("bt7s7k7/MiniML").res("miniML"),
)

project.script("leaf-gen", async (args) => {
    await require("./src/cli").executeCommand(args)
}, { desc: "Generates TreeBurst library documentation", argc: NaN })
