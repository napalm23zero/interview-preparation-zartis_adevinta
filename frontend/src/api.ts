export type Listing = {
  id: string;
  sellerId?: string;
  sellerDisplayName?: string;
  title: string;
  description: string;
  category: string;
  condition: string;
  city: string;
  price: number | null;
  currency: string;
  status?: string;
  version: number;
  interestCount?: number;
};
export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  total: number;
};
export type Identity = { id: string; displayName: string; role: string };
export type Generation = {
  id: string;
  mode: string;
  status: string;
  provider: string;
  model: string;
  suggestions: Record<string, string> | null;
};
export type Photo = { id: string; listingId: string; validationStatus: string };
export type Interest = { buyerDisplayName: string; createdAt: string };
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}

export async function api<T>(
  path: string,
  token?: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body) headers.set("Content-Type", "application/json");
  let response: Response;
  try {
    response = await fetch(`/api${path}`, { ...options, headers });
  } catch {
    throw new ApiError("Cannot reach the API. Please try again.", 0);
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    const message =
      problem?.detail ||
      problem?.title ||
      (response.status === 401
        ? "Your session has expired or the credentials are invalid."
        : `Request failed (${response.status}).`);
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export const json = (
  body: unknown,
  method = "POST",
  key?: string,
): RequestInit => ({
  method,
  body: JSON.stringify(body),
  headers: key ? { "Idempotency-Key": key } : {},
});
export const key = () => crypto.randomUUID();
export const price = (amount: number | null, currency = "CAD") =>
  amount == null
    ? "Price not set"
    : new Intl.NumberFormat("en-CA", { style: "currency", currency }).format(
        amount,
      );
export const label = (value: string | null | undefined) =>
  value
    ? value
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/^./, (c) => c.toUpperCase())
    : "Not specified";
