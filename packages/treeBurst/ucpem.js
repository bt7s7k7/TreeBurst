/// <reference path="./.vscode/config.d.ts" />

const { project, github } = require("ucpem")

project.isChild()

project.prefix("src").res("treeBurst",
    github("bt7s7k7/Vue3GUI").res("vue3gui"),
    github("bt7s7k7/CommonTypes").res("comTypes"),
    github("bt7s7k7/Apsides").res("editor"),
    github("bt7s7k7/LogLib").res("prettyPrint"),
    github("bt7s7k7/TextFormat").res("textFormatHTML"),
)
