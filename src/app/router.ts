import { h } from "vue"
import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router"
import { Editor } from "./Editor"

const routes: RouteRecordRaw[] = [
    {
        name: "Editor",
        path: "/",
        component: Editor,
    },
    {
        name: "404",
        component: { setup: () => () => h("pre", { class: "m-4" }, "Page not found") },
        path: "/:page(.*)*",
    },
]

export const router = createRouter({
    history: createWebHistory(),
    routes,
})
