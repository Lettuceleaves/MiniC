import "@testing-library/jest-dom/vitest";

import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";

import { App } from "../app/App";

describe("App", () => {
  test("renders the Workbench shell first", async () => {
    vi.spyOn(window, "scrollTo").mockImplementation(() => undefined);

    render(<App />);

    expect(await screen.findByTestId("workbench-shell")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /welcome/i })).not.toBeInTheDocument();
  });
});
