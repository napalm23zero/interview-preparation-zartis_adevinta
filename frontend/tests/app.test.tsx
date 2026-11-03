// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/App";

const listing = {
  id: "item-1",
  sellerId: "seller-1",
  title: "Training saber",
  description: "A trusty saber",
  category: "OTHER",
  condition: "GOOD",
  city: "Toronto",
  price: 45,
  currency: "CAD",
  sellerDisplayName: "Luke Skywalker",
  status: "DRAFT",
  version: 0,
  interestCount: 0,
};
const reply = (body: unknown, status = 200) =>
  new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
const page = (items: unknown[]) => ({
  content: items,
  page: 1,
  size: 10,
  total: items.length,
});

beforeEach(() => {
  sessionStorage.clear();
  history.replaceState({}, "", "/");
  vi.spyOn(window, "scrollTo").mockImplementation(() => {});
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("marketplace journey", () => {
  it("keeps existing photos when adding a fifth and disables further uploads", async () => {
    sessionStorage.setItem("loopmarket-session", "session");
    history.replaceState({}, "", "/listings/item-1/edit");
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: vi.fn(() => "blob:photo"), revokeObjectURL: vi.fn() }));
    const existing = Array.from({ length: 4 }, (_, index) => ({ id: `photo-${index}`, listingId: "item-1", validationStatus: "VALID" }));
    const fetchMock = vi.fn(async (url: string, options?: RequestInit) => {
      if (url === "/api/users/me") return reply({ id: "seller-1", displayName: "Luke", role: "USER" });
      if (url === "/api/users/me/listings/item-1") return reply(listing);
      if (url === "/api/generations/models") return reply({ models: ["vision"], defaultModel: "vision" });
      if (url.startsWith("/api/photos?page=")) return reply(page(existing));
      if (url === "/api/photos" && options?.method === "POST") return reply({ id: "photo-4", listingId: "item-1", validationStatus: "PENDING" });
      if (url === "/api/photos/photo-4/content" && options?.method === "PUT") return reply({ id: "photo-4", listingId: "item-1", validationStatus: "VALID" });
      if (url.startsWith("/api/photos/") && url.endsWith("/content")) return new Response(new Blob(["image"]), { status: 200 });
      return reply({}, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText(/4 of 5 uploaded/i);
    await user.upload(screen.getByLabelText("Choose file"), new File(["image"], "new.png", { type: "image/png" }));
    await user.click(screen.getByRole("button", { name: "Upload photo" }));
    await screen.findByText(/5 of 5 uploaded/i);
    expect(screen.getAllByRole("button", { name: "View photo" })).toHaveLength(5);
    expect((screen.getByLabelText("Choose file") as HTMLInputElement).disabled).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith("/api/photos/photo-4/content", expect.objectContaining({ method: "PUT" }));
  });
  it("browses, signs in and registers interest on the original listing", async () => {
    const fetchMock = vi.fn(async (url: string, options?: RequestInit) => {
      if (url.startsWith("/api/catalog?")) return reply(page([listing]));
      if (url === "/api/catalog/item-1") return reply(listing);
      if (url === "/api/catalog/item-1/photos") return reply(["photo-1", "photo-2"]);
      if (url === "/api/sessions") return reply({ accessToken: "session" });
      if (url === "/api/users/me")
        return reply({ id: "buyer-1", displayName: "Vader", role: "USER" });
      if (url === "/api/interests" && options?.method === "POST")
        return reply({ id: "interest-1" });
      return reply({}, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<App />);
    await user.click(
      await screen.findByRole("button", { name: "View Training saber" }),
    );
    await user.click(await screen.findByRole("button", { name: "View photo 2" }));
    expect((screen.getByAltText("Training saber") as HTMLImageElement).src).toContain("/photos/photo-2");
    await user.click(
      await screen.findByRole("button", { name: /i’m interested/i }),
    );
    await user.type(screen.getByLabelText("Username"), "vader");
    await user.type(screen.getByLabelText("Password"), "secret");
    await user.click(screen.getByRole("button", { name: /sign in →/i }));
    await user.click(
      await screen.findByRole("button", { name: /i’m interested/i }),
    );
    expect(await screen.findByText(/interest registered/i)).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/interests",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ listingId: "item-1" }),
      }),
    );
  });

  it("saves a draft, lets the seller review suggestions and publishes only after confirmation", async () => {
    sessionStorage.setItem("loopmarket-session", "session");
    history.replaceState({}, "", "/listings/new");
    const fetchMock = vi.fn(async (url: string, options?: RequestInit) => {
      if (url === "/api/users/me")
        return reply({ id: "seller-1", displayName: "Luke", role: "USER" });
      if (url === "/api/generations/models") return reply({ models: ["vision:latest", "other:latest"], defaultModel: "vision:latest" });
      if (url === "/api/listings" && options?.method === "POST")
        return reply(listing, 201);
      if (url === "/api/listings/item-1" && options?.method === "PUT")
        return reply({ ...listing, version: 1 });
      if (url === "/api/generations") return reply({ id: "generation-1", status: "PENDING" }, 201);
      if (url === "/api/generations/generation-1/execute?model=other%3Alatest")
        return reply(
          {
            id: "generation-1",
            mode: "DETERMINISTIC",
            status: "COMPLETED",
            suggestions: {
              title: "Polished saber",
              description: "A vintage saber",
              category: "ELECTRONICS",
              condition: "GOOD",
              price: "149.99",
            },
          },
          201,
        );
      if (url === "/api/listings/item-1/publications")
        return reply({ ...listing, status: "PUBLISHED" });
      if (url.startsWith("/api/users/me/listings?")) return reply(page([]));
      return reply({}, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    render(<App />);
    await user.selectOptions(await screen.findByLabelText("Local model"), "other:latest");
    await user.type(screen.getByLabelText("Title"), "Training saber");
    await user.click(
      screen.getByRole("button", { name: /generate suggestions/i }),
    );
    await screen.findByText(/suggestions filled the empty fields/i);
    expect(fetchMock).toHaveBeenCalledWith("/api/generations/generation-1/execute?model=other%3Alatest", expect.objectContaining({ method: "POST" }));
    expect((screen.getByLabelText("Description") as HTMLTextAreaElement).value).toBe("A vintage saber");
    expect((screen.getByLabelText("Title") as HTMLInputElement).value).toBe("Training saber");
    expect((screen.getByLabelText("Category") as HTMLSelectElement).value).toBe("ELECTRONICS");
    expect((screen.getByLabelText("Condition") as HTMLSelectElement).value).toBe("GOOD");
    expect((screen.getByLabelText("Price (CAD)") as HTMLInputElement).value).toBe("149.99");
    expect(screen.queryByRole("button", { name: /use suggestion/i })).toBeNull();
    await user.click(screen.getByRole("button", { name: /review & publish/i }));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/listings/item-1/publications",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    expect(window.confirm).toHaveBeenCalled();
  });

  it("protects unsaved edits when leaving the editor", async () => {
    sessionStorage.setItem("loopmarket-session", "session");
    history.replaceState({}, "", "/listings/new");
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        reply({ id: "seller-1", displayName: "Luke", role: "USER" }),
      ),
    );
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    render(<App />);
    await user.type(screen.getByLabelText("Title"), "Unfinished saber");
    await user.click(screen.getByRole("link", { name: "Discover" }));
    expect(confirm).toHaveBeenCalled();
    expect((screen.getByLabelText("Title") as HTMLInputElement).value).toBe(
      "Unfinished saber",
    );
  });

  it("keeps stale suggestions out when the editor changes during generation", async () => {
    sessionStorage.setItem("loopmarket-session", "session");
    history.replaceState({}, "", "/listings/new");
    let complete!: (response: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (url === "/api/users/me")
          return reply({ id: "seller-1", displayName: "Luke", role: "USER" });
        if (url === "/api/generations/models") return reply({ models: ["vision:latest"], defaultModel: "vision:latest" });
        if (url === "/api/listings") return reply(listing, 201);
        if (url === "/api/generations") return reply({ id: "g", status: "PENDING" }, 201);
        if (url === "/api/generations/g/execute?model=vision%3Alatest")
          return new Promise<Response>((resolve) => {
            complete = resolve;
          });
        return reply({}, 404);
      }),
    );
    const user = userEvent.setup();
    render(<App />);
    await user.type(screen.getByLabelText("Title"), "Training saber");
    await user.click(
      screen.getByRole("button", { name: /generate suggestions/i }),
    );
    await waitFor(() => expect(complete).toBeDefined());
    expect(screen.getByRole("status").textContent).toContain("Generating");
    // A programmatic input change exercises the race even while controls are disabled.
    fireEvent.change(screen.getByLabelText("Title"), {
      target: { value: "Newer edit" },
    });
    complete(
      reply({
        id: "g",
        status: "COMPLETED",
        suggestions: { title: "Stale output" },
      }),
    );
    expect(
      await screen.findByText(/editor changed while generating/i),
    ).toBeTruthy();
    expect(screen.queryByText("Stale output")).toBeNull();
  });
});
