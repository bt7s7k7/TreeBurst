import { createApp } from "vue"
import { App } from "./app/App"
import { router } from "./app/router"
import "./vue3gui/style.scss"
import { vue3gui } from "./vue3gui/vue3gui"

const app = createApp(App)

app.use(router)
app.use(vue3gui, {})

app.mount("#app")
