import { h } from "vue"
import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router"
import { EditorPage } from "./EditorPage"

const routes: RouteRecordRaw[] = [
    {
        name: "Editor",
        path: "/editor",
        component: EditorPage,
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
