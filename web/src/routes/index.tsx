import { createRootRoute, createRoute, createRouter, Outlet, RouterProvider } from "@tanstack/react-router";

import { WorkbenchLayout } from "../layouts/workbench/WorkbenchLayout";

const rootRoute = createRootRoute({
  component: () => <Outlet />,
});

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: WorkbenchLayout,
});

const routeTree = rootRoute.addChildren([indexRoute]);

export const router = createRouter({
  routeTree,
  defaultPreload: "intent",
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}

export function AppRoutes() {
  return <RouterProvider router={router} />;
}
