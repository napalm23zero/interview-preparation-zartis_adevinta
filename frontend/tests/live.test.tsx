// @vitest-environment jsdom
import { readFileSync } from "node:fs";
import { afterEach, expect, it, vi } from "vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/App";

const enabled = process.env.LOOPMARKET_LIVE_TEST === "1";
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it.skipIf(!enabled)(
  "runs a seller-to-buyer journey through the local gateway",
  async () => {
    const nativeFetch = globalThis.fetch;
    vi.stubGlobal("fetch", (url: string, options?: RequestInit) =>
      nativeFetch(`http://127.0.0.1:8088${url}`, options),
    );
    vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    vi.spyOn(window, "confirm").mockReturnValue(true);
    sessionStorage.clear();
    history.replaceState({}, "", "/login");
    const user = userEvent.setup();
    render(<App />);
    await user.type(screen.getByLabelText("Username"), "luke.skywalker");
    await user.type(screen.getByLabelText("Password"), "force-1977");
    await user.click(screen.getByRole("button", { name: /sign in →/i }));
    await user.click(
      await screen.findByRole("button", { name: /create listing/i }),
    );
    const title = `Rebel field kit ${Date.now()}`;
    await user.type(screen.getByLabelText("Title"), title);
    await user.type(
      screen.getByLabelText("Description"),
      "A lightly used field kit for adventures across the galaxy.",
    );
    await user.selectOptions(screen.getByLabelText("Category"), "OTHER");
    await user.selectOptions(screen.getByLabelText("Condition"), "GOOD");
    await user.type(screen.getByLabelText("City"), "Tatooine");
    await user.type(screen.getByLabelText("Price (CAD)"), "42.50");
    const bytes = readFileSync(
      "../backend/app/src/main/resources/db/demo/photos/01900000-0000-7000-8000-000000000501.png",
    );
    await user.upload(
      screen.getByLabelText("Choose file"),
      new File([bytes], "field-kit.png", { type: "image/png" }),
    );
    await user.click(screen.getByRole("button", { name: "Upload photo" }));
    await screen.findByText(
      /photo uploaded and validated/i,
      {},
      { timeout: 10000 },
    );
    await user.click(
      screen.getByRole("button", { name: /generate suggestions/i }),
    );
    await screen.findByText("Review suggestions", {}, { timeout: 10000 });
    await user.click(screen.getByRole("button", { name: /review & publish/i }));
    await screen.findByText(title, {}, { timeout: 10000 });
    await user.click(screen.getByRole("button", { name: "Sign out" }));
    await screen.findByRole("heading", { name: /good things deserve/i });
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    await user.type(screen.getByLabelText("Username"), "leia.organa");
    await user.type(screen.getByLabelText("Password"), "rebel-1977");
    await user.click(screen.getByRole("button", { name: /sign in →/i }));
    await screen.findByRole("heading", { name: "My listings" });
    await user.click(screen.getByRole("link", { name: "Discover" }));
    await user.type(screen.getByLabelText("Search listings"), title);
    await user.click(screen.getByRole("button", { name: "Search" }));
    await user.click(
      await screen.findByRole(
        "button",
        { name: `View ${title}` },
        { timeout: 10000 },
      ),
    );
    await user.click(
      await screen.findByRole("button", { name: /i’m interested/i }),
    );
    await screen.findByText(/interest registered/i, {}, { timeout: 10000 });
    await user.click(screen.getByRole("button", { name: "Sign out" }));
    await screen.findByRole("heading", { name: /good things deserve/i });
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    await user.type(screen.getByLabelText("Username"), "luke.skywalker");
    await user.type(screen.getByLabelText("Password"), "force-1977");
    await user.click(screen.getByRole("button", { name: /sign in →/i }));
    await screen.findByText(title, {}, { timeout: 10000 });
    const row = screen.getByText(title).closest(".seller-row") as HTMLElement;
    await waitFor(
      () =>
        expect(
          within(row).getByRole("button", { name: /1 interested/i }),
        ).toBeTruthy(),
      { timeout: 10000 },
    );
    await user.click(
      within(row).getByRole("button", { name: /1 interested/i }),
    );
    await within(row).findByText(/Leia Organa/);
  },
  60000,
);
