import { h } from "vue"
import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router"

const routes: RouteRecordRaw[] = [
    {
        name: "Editor",
        path: "/editor",
        component: () => import("./EditorPage").then(v => v.EditorPage),
    },
    {
        name: "Test",
        path: "/test",
        component: () => import("./TestPage").then(v => v.TestPage),
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
