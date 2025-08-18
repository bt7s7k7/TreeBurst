import { h } from "vue"
import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router"

const routes: RouteRecordRaw[] = [
    // {
    //     name: "Home",
    //     path: "/",
    //     component: Home
    // },
    {
        name: "404",
        component: { setup: () => () => h("pre", { class: "m-4" }, "Page not found") },
        path: "/:page(.*)*"
    }
]

export const router = createRouter({
    history: createWebHistory(),
    routes
})
