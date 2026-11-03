import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import {
  api,
  ApiError,
  Generation,
  Identity,
  Interest,
  json,
  key,
  label,
  Listing,
  Page,
  Photo,
  price,
} from "./api";

type Navigate = (path: string) => void;
const categories = [
  "ELECTRONICS",
  "HOME",
  "FASHION",
  "SPORTS",
  "BOOKS",
  "OTHER",
];
const conditions = ["NEW", "LIKE_NEW", "GOOD", "FAIR", "USED"];
const empty = {
  title: "",
  description: "",
  category: "",
  condition: "",
  city: "",
  price: "",
  currency: "CAD",
};
type Fields = typeof empty;
const message = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
const fieldsFrom = (item: Listing): Fields => ({
  title: item.title || "",
  description: item.description || "",
  category: item.category || "",
  condition: item.condition || "",
  city: item.city || "",
  price: item.price == null ? "" : String(item.price),
  currency: item.currency || "CAD",
});
const payload = (fields: Fields, expectedVersion?: number) => ({
  ...fields,
  category: fields.category || null,
  condition: fields.condition || null,
  price: fields.price === "" ? null : fields.price,
  expectedVersion: expectedVersion ?? null,
});

function App() {
  const [path, setPath] = useState(location.pathname);
  const [token, setToken] = useState(
    () => sessionStorage.getItem("loopmarket-session") || "",
  );
  const [user, setUser] = useState<Identity | null>(null);
  const [authError, setAuthError] = useState("");
  const dirty = useRef(false);
  const markDirty = useCallback((value: boolean) => {
    dirty.current = value;
  }, []);
  const navigate: Navigate = useCallback((next) => {
    if (dirty.current && !window.confirm("Discard unsaved changes?")) return;
    dirty.current = false;
    history.pushState({}, "", next);
    setPath(next);
    window.scrollTo(0, 0);
  }, []);
  useEffect(() => {
    const change = () => {
      if (dirty.current && !window.confirm("Discard unsaved changes?")) {
        history.pushState({}, "", path);
        return;
      }
      dirty.current = false;
      setPath(location.pathname);
    };
    const unload = (event: BeforeUnloadEvent) => {
      if (dirty.current) event.preventDefault();
    };
    window.addEventListener("popstate", change);
    window.addEventListener("beforeunload", unload);
    return () => {
      window.removeEventListener("popstate", change);
      window.removeEventListener("beforeunload", unload);
    };
  }, [path]);
  useEffect(() => {
    if (!token) {
      setUser(null);
      return;
    }
    let active = true;
    api<Identity>("/users/me", token)
      .then((result) => {
        if (active) setUser(result);
      })
      .catch((error) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 401) {
          sessionStorage.removeItem("loopmarket-session");
          setToken("");
        } else setAuthError(message(error));
      });
    return () => {
      active = false;
    };
  }, [token]);
  const logout = async () => {
    if (
      dirty.current &&
      !window.confirm("Discard unsaved changes and sign out?")
    )
      return;
    try {
      await api("/sessions", token, { method: "DELETE" });
      sessionStorage.removeItem("loopmarket-session");
      setToken("");
      setUser(null);
      setAuthError("");
      dirty.current = false;
      navigate("/");
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        sessionStorage.removeItem("loopmarket-session");
        setToken("");
        setUser(null);
        dirty.current = false;
        navigate("/");
      } else setAuthError(`Could not confirm sign out: ${message(error)}`);
    }
  };
  const login = (value: string) => {
    sessionStorage.setItem("loopmarket-session", value);
    setToken(value);
    setAuthError("");
  };
  const link = (event: React.MouseEvent<HTMLAnchorElement>, to: string) => {
    if (
      !event.metaKey &&
      !event.ctrlKey &&
      !event.shiftKey &&
      !event.altKey &&
      event.button === 0
    ) {
      event.preventDefault();
      navigate(to);
    }
  };
  return (
    <>
      <header className="header">
        <div className="nav">
          <a className="brand" href="/" onClick={(e) => link(e, "/")}>
            <span className="brand-icon">↻</span> loopmarket
            <span className="brand-dot">.</span>
          </a>
          <nav aria-label="Main navigation">
            <a href="/" onClick={(e) => link(e, "/")}>
              Discover
            </a>
            {token && (
              <a href="/dashboard" onClick={(e) => link(e, "/dashboard")}>
                My listings
              </a>
            )}
          </nav>
          <div className="nav-actions">
            {token ? (
              <>
                <span className="user-name">
                  {user?.displayName || "My account"}
                </span>
                <button className="text-button" onClick={logout}>
                  Sign out
                </button>
              </>
            ) : (
              <button
                className="text-button"
                onClick={() => navigate("/login")}
              >
                Sign in
              </button>
            )}
            <button
              className="button primary"
              aria-label="Sell an item"
              onClick={() => navigate(token ? "/listings/new" : "/login")}
            >
              ＋ <span>Sell an item</span>
            </button>
          </div>
        </div>
      </header>
      {authError && (
        <div className="notice error" role="alert">
          {authError}
        </div>
      )}
      <main id="main">
        {path === "/" ? (
          <Catalog navigate={navigate} />
        ) : path === "/login" ? (
          <Login onLogin={login} navigate={navigate} />
        ) : path === "/dashboard" ? (
          <Dashboard token={token} navigate={navigate} />
        ) : path === "/listings/new" ? (
          <Editor token={token} navigate={navigate} markDirty={markDirty} />
        ) : /^\/listings\/[^/]+\/edit$/.test(path) ? (
          <Editor
            token={token}
            navigate={navigate}
            markDirty={markDirty}
            id={path.split("/")[2]}
          />
        ) : /^\/listings\/[^/]+$/.test(path) ? (
          <Detail
            id={path.split("/")[2]}
            token={token}
            user={user}
            navigate={navigate}
          />
        ) : (
          <div className="wrap empty">
            <h1>Page not found</h1>
            <button className="button primary" onClick={() => navigate("/")}>
              Back to discovery
            </button>
          </div>
        )}
      </main>
      <footer>
        <div className="wrap footer">
          <span className="brand">
            ↻ loopmarket<span className="brand-dot">.</span>
          </span>
          <span>Good things deserve another life.</span>
          <span>Made for a more circular galaxy ✦</span>
        </div>
      </footer>
    </>
  );
}

function ErrorBox({ value }: { value: string }) {
  return value ? (
    <div className="notice error" role="alert">
      {value}
    </div>
  ) : null;
}
function Card({ item, navigate }: { item: Listing; navigate: Navigate }) {
  return (
    <article className="card">
      <button
        className="card-link"
        onClick={() => navigate(`/listings/${item.id}`)}
        aria-label={`View ${item.title}`}
      >
        <div className="card-image">
          <img src={`/api/catalog/${item.id}/photo`} alt="" loading="lazy" />
          <span className="badge">{label(item.category)}</span>
        </div>
        <div className="card-body">
          <div className="card-meta">
            {label(item.condition)} <span>·</span> {item.city}
          </div>
          <h3>{item.title}</h3>
          <div className="card-bottom">
            <strong>{price(item.price, item.currency)}</strong>
            <span>↗</span>
          </div>
          <p>by {item.sellerDisplayName}</p>
        </div>
      </button>
    </article>
  );
}
function Catalog({ navigate }: { navigate: Navigate }) {
  const [filters, setFilters] = useState({
    text: "",
    category: "",
    condition: "",
    city: "",
    minPrice: "",
    maxPrice: "",
  });
  const [query, setQuery] = useState(filters);
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Page<Listing> | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    const params = new URLSearchParams({ page: String(page), size: "8" });
    Object.entries(query).forEach(([k, v]) => {
      if (v.trim()) params.set(k, v.trim());
    });
    api<Page<Listing>>(`/catalog?${params}`)
      .then((result) => {
        if (active) setData(result);
      })
      .catch((e) => {
        if (active) setError(message(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [query, page]);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    setPage(1);
    setQuery(filters);
  };
  return (
    <>
      <section className="hero">
        <div className="wrap hero-inner">
          <div>
            <div className="eyebrow">
              THE CIRCULAR MARKETPLACE <span>✦</span>
            </div>
            <h1>
              Good things deserve
              <br />
              <em>another life.</em>
            </h1>
            <p>
              Discover pre-loved finds, make someone's day, and keep great
              things in the loop.
            </p>
            <button
              className="button primary"
              onClick={() =>
                document
                  .getElementById("discover")
                  ?.scrollIntoView({ behavior: "smooth" })
              }
            >
              Explore the marketplace <span>→</span>
            </button>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="orbit orbit-one"></div>
            <div className="orbit orbit-two"></div>
            <div className="hero-symbol">↻</div>
            <span className="hero-star">✦</span>
            <div className="hero-note">
              A little better
              <br />
              <b>the second time around.</b>
            </div>
          </div>
        </div>
      </section>
      <section className="wrap discovery" id="discover">
        <div className="section-heading">
          <div>
            <div className="eyebrow">FIND YOUR NEXT FAVORITE</div>
            <h2>Discover something great</h2>
            <p>Thoughtful finds from people in your community.</p>
          </div>
          <span className="count">{data ? `${data.total} items` : ""}</span>
        </div>
        <form className="filters" onSubmit={submit}>
          <label className="search-label">
            <span className="sr-only">Search listings</span>
            <span aria-hidden="true">⌕</span>
            <input
              aria-label="Search listings"
              placeholder="Search for anything..."
              value={filters.text}
              onChange={(e) => setFilters({ ...filters, text: e.target.value })}
            />
          </label>
          <label>
            <span className="sr-only">Category</span>
            <select
              value={filters.category}
              onChange={(e) =>
                setFilters({ ...filters, category: e.target.value })
              }
            >
              <option value="">All categories</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {label(c)}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="sr-only">Condition</span>
            <select
              value={filters.condition}
              onChange={(e) =>
                setFilters({ ...filters, condition: e.target.value })
              }
            >
              <option value="">Any condition</option>
              {conditions.map((c) => (
                <option key={c} value={c}>
                  {label(c)}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="sr-only">City</span>
            <input
              placeholder="City"
              value={filters.city}
              onChange={(e) => setFilters({ ...filters, city: e.target.value })}
            />
          </label>
          <label>
            <span className="sr-only">Minimum price</span>
            <input
              type="number"
              min="0"
              step="0.01"
              placeholder="Min $"
              value={filters.minPrice}
              onChange={(e) =>
                setFilters({ ...filters, minPrice: e.target.value })
              }
            />
          </label>
          <label>
            <span className="sr-only">Maximum price</span>
            <input
              type="number"
              min="0"
              step="0.01"
              placeholder="Max $"
              value={filters.maxPrice}
              onChange={(e) =>
                setFilters({ ...filters, maxPrice: e.target.value })
              }
            />
          </label>
          <button className="button primary">Search</button>
        </form>
        <div className="chips">
          <button
            className={!query.category ? "active" : ""}
            onClick={() => {
              setFilters({ ...filters, category: "" });
              setQuery({ ...query, category: "" });
              setPage(1);
            }}
          >
            All items
          </button>
          {categories.map((c) => (
            <button
              key={c}
              className={query.category === c ? "active" : ""}
              onClick={() => {
                setFilters({ ...filters, category: c });
                setQuery({ ...query, category: c });
                setPage(1);
              }}
            >
              {label(c)}
            </button>
          ))}
        </div>
        <ErrorBox value={error} />
        {loading ? (
          <p className="empty" role="status">
            Finding good things...
          </p>
        ) : !error && data?.content.length === 0 ? (
          <p className="empty">No items found. Try a different search.</p>
        ) : (
          <div className="grid">
            {data?.content.map((item) => (
              <Card key={item.id} item={item} navigate={navigate} />
            ))}
          </div>
        )}
        {data && data.total > data.size && (
          <div className="pagination">
            <button
              className="button secondary"
              disabled={page === 1}
              onClick={() => setPage(page - 1)}
            >
              ← Previous
            </button>
            <span>
              Page {page} of {Math.ceil(data.total / data.size)}
            </span>
            <button
              className="button secondary"
              disabled={page * data.size >= data.total}
              onClick={() => setPage(page + 1)}
            >
              Next →
            </button>
          </div>
        )}
      </section>
    </>
  );
}

function Login({
  onLogin,
  navigate,
}: {
  onLogin: (token: string) => void;
  navigate: Navigate;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api<{ accessToken: string }>(
        "/sessions",
        undefined,
        json({ username, password }),
      );
      onLogin(result.accessToken);
      const destination =
        sessionStorage.getItem("loopmarket-return") || "/dashboard";
      sessionStorage.removeItem("loopmarket-return");
      navigate(destination);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="wrap auth-wrap">
      <div className="panel auth">
        <div className="eyebrow">WELCOME BACK</div>
        <h1>Sign in to your loop.</h1>
        <p>Ready to give good things another life?</p>
        <ErrorBox value={error} />
        <form onSubmit={submit} className="form-stack">
          <label>
            Username
            <input
              autoComplete="username"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          <button className="button primary" disabled={busy}>
            {busy ? "Signing in..." : "Sign in →"}
          </button>
        </form>
        <div className="helper">
          Local demo accounts are listed in the project README.
        </div>
      </div>
    </div>
  );
}
function RequireAuth({ navigate }: { navigate: Navigate }) {
  return (
    <div className="wrap empty">
      <h1>Sign in to continue</h1>
      <p>Your listings and activity are waiting for you.</p>
      <button className="button primary" onClick={() => navigate("/login")}>
        Sign in
      </button>
    </div>
  );
}

function Detail({
  id,
  token,
  user,
  navigate,
}: {
  id: string;
  token: string;
  user: Identity | null;
  navigate: Navigate;
}) {
  const [item, setItem] = useState<Listing | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    api<Listing>(`/catalog/${id}`)
      .then((value) => {
        if (active) setItem(value);
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [id]);
  const interest = async () => {
    if (!token) {
      sessionStorage.setItem("loopmarket-return", `/listings/${id}`);
      navigate("/login");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await api("/interests", token, json({ listingId: id }));
      setSuccess("Interest registered! The seller can see your display name.");
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="wrap page">
      <button className="back" onClick={() => navigate("/")}>
        ← Back to discovery
      </button>
      <ErrorBox value={error} />
      {!item && !error && <p role="status">Loading listing...</p>}
      {item && (
        <div className="detail-grid">
          <div className="detail-image">
            <img src={`/api/catalog/${id}/photo`} alt={item.title} />
          </div>
          <div className="detail-info">
            <div className="eyebrow">
              {label(item.category)} <span> / </span> {label(item.condition)}
            </div>
            <h1>{item.title}</h1>
            <div className="detail-price">
              {price(item.price, item.currency)}
            </div>
            <div className="location">⌖ {item.city}</div>
            <hr />
            <div className="seller">
              <div className="avatar">
                {item.sellerDisplayName?.charAt(0) || "L"}
              </div>
              <div>
                <small>LISTED BY</small>
                <strong>{item.sellerDisplayName}</strong>
              </div>
            </div>
            <hr />
            <h2>About this item</h2>
            <p className="description">{item.description}</p>
            <div className="detail-facts">
              <span>
                Condition <b>{label(item.condition)}</b>
              </span>
              <span>
                Category <b>{label(item.category)}</b>
              </span>
            </div>
            {success ? (
              <div className="notice success" role="status">
                {success}
              </div>
            ) : (
              <button
                className="button primary full"
                disabled={busy}
                onClick={interest}
              >
                {busy ? "Saving interest..." : "♡  I’m interested"}
              </button>
            )}
            <p className="fine-print">
              Interest is a signal to the seller. LoopMarket does not provide
              messaging or payments.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function Dashboard({ token, navigate }: { token: string; navigate: Navigate }) {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Page<Listing> | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState("");
  const [interests, setInterests] = useState<Interest[]>([]);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!token) return;
    let active = true;
    setLoading(true);
    setError("");
    const params = new URLSearchParams({ page: String(page), size: "10" });
    if (status) params.set("status", status);
    api<Page<Listing>>(`/users/me/listings?${params}`, token)
      .then((result) => {
        if (active) setData(result);
      })
      .catch((e) => {
        if (active) setError(message(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [token, status, page, revision]);
  if (!token) return <RequireAuth navigate={navigate} />;
  const showInterests = async (id: string) => {
    if (selected === id) {
      setSelected("");
      return;
    }
    try {
      const result = await api<Interest[]>(
        `/users/me/listings/${id}/interests`,
        token,
      );
      setInterests(result);
      setSelected(id);
    } catch (e) {
      setError(message(e));
    }
  };
  const remove = async (item: Listing) => {
    if (
      !window.confirm(
        `Delete “${item.title || "Untitled draft"}”? It will be removed from your listings.`,
      )
    )
      return;
    try {
      await api(`/listings/${item.id}`, token, { method: "DELETE" });
      setPage(1);
      setRevision(revision + 1);
    } catch (e) {
      setError(message(e));
    }
  };
  const archive = async (item: Listing) => {
    if (
      !window.confirm(
        `Archive “${item.title}”? It will no longer appear in the catalog.`,
      )
    )
      return;
    try {
      await api(
        `/listings/${item.id}/archives`,
        token,
        json({ expectedVersion: item.version }),
      );
      setRevision(revision + 1);
    } catch (e) {
      setError(message(e));
    }
  };
  return (
    <div className="wrap page">
      <div className="section-heading">
        <div>
          <div className="eyebrow">YOUR LITTLE CORNER OF THE LOOP</div>
          <h1>My listings</h1>
          <p>Manage your items and see who's interested.</p>
        </div>
        <button
          className="button primary"
          onClick={() => navigate("/listings/new")}
        >
          ＋ Create listing
        </button>
      </div>
      <div className="chips">
        {["", "DRAFT", "PUBLISHED", "ARCHIVED"].map((s) => (
          <button
            key={s}
            className={status === s ? "active" : ""}
            onClick={() => {
              setStatus(s);
              setPage(1);
              setSelected("");
            }}
          >
            {s ? label(s) : "All listings"}
          </button>
        ))}
      </div>
      <ErrorBox value={error} />
      {loading ? (
        <p role="status">Loading your listings...</p>
      ) : !error && !data?.content.length ? (
        <div className="panel empty">
          <h2>Nothing here yet</h2>
          <p>Give something a new story.</p>
          <button
            className="button primary"
            onClick={() => navigate("/listings/new")}
          >
            Create a listing
          </button>
        </div>
      ) : (
        <div className="seller-list">
          {data?.content.map((item) => (
            <div className="panel seller-row" key={item.id}>
              <div className="seller-row-main">
                <div>
                  <span className={`status ${item.status?.toLowerCase()}`}>
                    {label(item.status)}
                  </span>
                  <h3>{item.title || "Untitled draft"}</h3>
                  <p>
                    {price(item.price, item.currency)} ·{" "}
                    {item.city || "No city yet"}
                  </p>
                </div>
                <div className="row-actions">
                  {item.status !== "ARCHIVED" && (
                    <button
                      className="button secondary"
                      onClick={() => navigate(`/listings/${item.id}/edit`)}
                    >
                      Edit
                    </button>
                  )}
                  {item.status === "DRAFT" && (
                    <button
                      className="text-button"
                      onClick={() => remove(item)}
                    >
                      Delete draft
                    </button>
                  )}
                  {item.status === "PUBLISHED" && (
                    <>
                      <button
                        className="button secondary"
                        onClick={() => showInterests(item.id)}
                      >
                        ♡ {item.interestCount} interested
                      </button>
                      <button
                        className="text-button"
                        onClick={() => archive(item)}
                      >
                        Archive
                      </button>
                    </>
                  )}
                </div>
              </div>
              {selected === item.id && (
                <div className="interest-list">
                  <h4>Interested buyers</h4>
                  {interests.length ? (
                    interests.map((interest, i) => (
                      <p key={`${interest.buyerDisplayName}-${i}`}>
                        {interest.buyerDisplayName} ·{" "}
                        {new Date(interest.createdAt).toLocaleDateString(
                          "en-CA",
                        )}
                      </p>
                    ))
                  ) : (
                    <p>No interest yet.</p>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {data && data.total > data.size && (
        <div className="pagination">
          <button
            className="button secondary"
            disabled={page === 1}
            onClick={() => setPage(page - 1)}
          >
            ← Previous
          </button>
          <span>
            Page {page} of {Math.ceil(data.total / data.size)}
          </span>
          <button
            className="button secondary"
            disabled={page * data.size >= data.total}
            onClick={() => setPage(page + 1)}
          >
            Next →
          </button>
        </div>
      )}
    </div>
  );
}

function Editor({
  token,
  navigate,
  id,
  markDirty,
}: {
  token: string;
  navigate: Navigate;
  id?: string;
  markDirty: (value: boolean) => void;
}) {
  const [item, setItem] = useState<Listing | null>(null);
  const [fields, setFields] = useState<Fields>(empty);
  const [photo, setPhoto] = useState<Photo | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [publicationUncertain, setPublicationUncertain] = useState(false);
  const [loading, setLoading] = useState(!!id);
  const [mode, setMode] = useState("DETERMINISTIC");
  const [suggestions, setSuggestions] = useState<Generation | null>(null);
  const revision = useRef(0);
  const savedRevision = useRef(0);
  const createKey = useRef(key());
  const createPayload = useRef<ReturnType<typeof payload> | null>(null);
  const publicationKey = useRef(key());
  useEffect(() => {
    if (!id || !token) return;
    let active = true;
    setLoading(true);
    (async () => {
      try {
        const result = await api<Listing>(`/users/me/listings/${id}`, token);
        let page = 1;
        let found: Photo | undefined;
        let response: Page<Photo>;
        do {
          response = await api<Page<Photo>>(
            `/photos?page=${page}&size=50`,
            token,
          );
          found = response.content.find((p) => p.listingId === id);
          page++;
        } while (!found && (page - 1) * response.size < response.total);
        if (active) {
          setItem(result);
          setFields(fieldsFrom(result));
          setPhoto(found || null);
        }
      } catch (e) {
        if (active) setError(message(e));
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [id, token]);
  if (!token) return <RequireAuth navigate={navigate} />;
  const change = (name: keyof Fields, value: string) => {
    revision.current++;
    markDirty(true);
    setSuggestions(null);
    setFields((current) => ({ ...current, [name]: value }));
  };
  // Keep request identities stable across retries; duplicate transmissions are no match for the Force.
  const save = async () => {
    if (item) {
      const saved = await api<Listing>(
        `/listings/${item.id}`,
        token,
        json(payload(fields, item.version), "PUT"),
      );
      setItem(saved);
      savedRevision.current = revision.current;
      markDirty(!!file);
      return saved;
    }
    const current = payload(fields);
    createPayload.current ||= current;
    let saved = await api<Listing>(
      "/listings",
      token,
      json(createPayload.current, "POST", createKey.current),
    );
    setItem(saved);
    history.replaceState({}, "", `/listings/${saved.id}/edit`);
    if (JSON.stringify(current) !== JSON.stringify(createPayload.current)) {
      saved = await api<Listing>(
        `/listings/${saved.id}`,
        token,
        json(payload(fields, saved.version), "PUT"),
      );
      setItem(saved);
    }
    savedRevision.current = revision.current;
    markDirty(!!file);
    return saved;
  };
  const saveClick = async () => {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await save();
      setNotice("Your changes have been saved.");
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  const upload = async (listing: Listing) => {
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type) || file.size === 0)
      throw new Error("Choose a non-empty JPEG or PNG.");
    let current = photo;
    if (!current) {
      current = await api<Photo>(
        "/photos",
        token,
        json({
          listingId: listing.id,
          mediaType: file.type,
          byteSize: file.size,
        }),
      );
      setPhoto(current);
    } else if (current.validationStatus === "PENDING") {
      current = await api<Photo>(
        `/photos/${current.id}`,
        token,
        json(
          { listingId: listing.id, mediaType: file.type, byteSize: file.size },
          "PUT",
        ),
      );
      setPhoto(current);
    }
    const base64 = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(",")[1]);
      reader.onerror = () => reject(new Error("Could not read the image."));
      reader.readAsDataURL(file);
    });
    const uploaded = await api<Photo>(
      `/photos/${current.id}/content`,
      token,
      json({ base64 }, "PUT"),
    );
    setPhoto(uploaded);
    setFile(null);
    markDirty(revision.current !== savedRevision.current);
  };
  const uploadClick = async () => {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const saved = item || (await save());
      await upload(saved);
      setNotice("Photo uploaded and validated.");
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  const generate = async () => {
    setBusy(true);
    setError("");
    setNotice("");
    setSuggestions(null);
    const requested = revision.current;
    try {
      const saved = await save();
      const requestKey = key();
      let result = await api<Generation>(
        "/generations",
        token,
        json({
          listingId: saved.id,
          requestKey,
          inputVersion: saved.version,
          clientRevision: String(requested),
          mode,
        }),
      );
      if (mode === "LOCAL") {
        result = await api<Generation>(
          `/generations/${result.id}/execute`,
          token,
          { method: "POST" },
        );
        while (result.status === "PROCESSING") {
          await new Promise((resolve) => setTimeout(resolve, 2000));
          result = await api<Generation>(`/generations/${result.id}`, token);
        }
      }
      if (requested !== revision.current) {
        setNotice(
          "The editor changed while generating. Suggestions were discarded; request new ones.",
        );
        return;
      }
      if (result.status !== "SUCCEEDED" && result.status !== "COMPLETED") {
        setError(
          `Generation ${label(result.status).toLowerCase()}. You can continue editing manually.`,
        );
        return;
      }
      setSuggestions(result);
    } catch (e) {
      setError(`${message(e)} You can continue editing manually.`);
    } finally {
      setBusy(false);
    }
  };
  const publish = async () => {
    if (
      !window.confirm(
        "Publish this listing? Its details and photo will be visible to everyone.",
      )
    )
      return;
    setBusy(true);
    setError("");
    setNotice("");
    let attempted = false;
    try {
      const saved = await save();
      if (file) await upload(saved);
      attempted = true;
      await api(
        `/listings/${saved.id}/publications`,
        token,
        json(
          { expectedVersion: saved.version },
          "POST",
          publicationKey.current,
        ),
      );
      markDirty(false);
      navigate("/dashboard");
    } catch (e) {
      if (
        attempted &&
        e instanceof ApiError &&
        (e.status === 0 || e.status >= 500)
      ) {
        setPublicationUncertain(true);
        setError(
          "Publication could not be confirmed. Reload this listing to check its status before trying again.",
        );
      } else
        setError(
          e instanceof ApiError && e.status === 409
            ? `${message(e)} Reload the listing if another session changed it.`
            : message(e),
        );
    } finally {
      setBusy(false);
    }
  };
  if (loading)
    return (
      <div className="wrap page" role="status">
        Loading your listing...
      </div>
    );
  if (id && !item)
    return (
      <div className="wrap page">
        <ErrorBox value={error} />
        <button
          className="button secondary"
          onClick={() => navigate("/dashboard")}
        >
          Back to my listings
        </button>
      </div>
    );
  const disabled = busy || item?.status === "ARCHIVED";
  return (
    <div className="wrap page">
      <button className="back" onClick={() => navigate("/dashboard")}>
        ← Back to my listings
      </button>
      <div className="section-heading">
        <div>
          <div className="eyebrow">GIVE SOMETHING A SECOND LIFE</div>
          <h1>{item ? "Edit listing" : "Create a listing"}</h1>
          <p>
            Share the details. You are always in control of what gets published.
          </p>
        </div>
        {item && (
          <span className={`status ${item.status?.toLowerCase()}`}>
            {label(item.status)}
          </span>
        )}
      </div>
      <ErrorBox value={error} />
      {notice && (
        <div className="notice success" role="status">
          {notice}
        </div>
      )}
      <div className="editor-grid">
        <div className="editor-main">
          <section className="panel form-section">
            <h2>Item photo</h2>
            <p>One clear photo helps your item find its next home.</p>
            <div className="upload">
              <div className="upload-icon">⇧</div>
              <b>
                {file
                  ? file.name
                  : photo?.validationStatus === "VALID"
                    ? "Photo ready"
                    : "Choose a photo"}
              </b>
              <span>JPEG or PNG, up to 20 MiB by default</span>
              <label className="button secondary file-button">
                Choose file
                <input
                  type="file"
                  accept="image/jpeg,image/png"
                  disabled={disabled}
                  onChange={(e) => {
                    setFile(e.target.files?.[0] || null);
                    markDirty(true);
                  }}
                />
              </label>
              {file && (
                <button
                  className="button secondary"
                  disabled={disabled}
                  onClick={uploadClick}
                >
                  Upload photo
                </button>
              )}
            </div>
          </section>
          <section className="panel form-section">
            <h2>Item details</h2>
            <p>Tell buyers the important bits.</p>
            <div className="form-stack">
              <label>
                Title
                <input
                  maxLength={120}
                  value={fields.title}
                  disabled={disabled}
                  onChange={(e) => change("title", e.target.value)}
                  placeholder="What are you selling?"
                />
              </label>
              <label>
                Description
                <textarea
                  maxLength={3000}
                  rows={5}
                  value={fields.description}
                  disabled={disabled}
                  onChange={(e) => change("description", e.target.value)}
                  placeholder="Describe the item, its condition, and what makes it special."
                />
              </label>
              <div className="form-two">
                <label>
                  Category
                  <select
                    value={fields.category}
                    disabled={disabled}
                    onChange={(e) => change("category", e.target.value)}
                  >
                    <option value="">Select category</option>
                    {categories.map((c) => (
                      <option key={c} value={c}>
                        {label(c)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Condition
                  <select
                    value={fields.condition}
                    disabled={disabled}
                    onChange={(e) => change("condition", e.target.value)}
                  >
                    <option value="">Select condition</option>
                    {conditions.map((c) => (
                      <option key={c} value={c}>
                        {label(c)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  City
                  <input
                    maxLength={80}
                    value={fields.city}
                    disabled={disabled}
                    onChange={(e) => change("city", e.target.value)}
                    placeholder="e.g. Toronto"
                  />
                </label>
                <label>
                  Price (CAD)
                  <input
                    type="number"
                    min="0"
                    max="9999999999.99"
                    step="0.01"
                    value={fields.price}
                    disabled={disabled}
                    onChange={(e) => change("price", e.target.value)}
                    placeholder="0.00"
                  />
                </label>
              </div>
            </div>
          </section>
        </div>
        <aside>
          <section className="panel ai-panel">
            <div className="ai-icon">✦</div>
            <div className="eyebrow ai-label">YOUR CREATIVE CO-PILOT</div>
            <h2>Need a little inspiration?</h2>
            <p>
              Generate suggestions from your saved item details. Review every
              suggestion before applying it.
            </p>
            <label>
              Suggestion mode
              <select
                value={mode}
                disabled={disabled || item?.status === "PUBLISHED"}
                onChange={(e) => setMode(e.target.value)}
              >
                <option value="DETERMINISTIC">Demo suggestions</option>
                <option value="LOCAL">Local model</option>
              </select>
            </label>
            <button
              className="button ai-button full"
              disabled={disabled || item?.status === "PUBLISHED"}
              onClick={generate}
            >
              ✦ Generate suggestions
            </button>
            <p className="fine-print">
              Demo mode is deterministic. Local mode uses the configured local
              gateway. Suggestions may be inaccurate.
            </p>
            <div aria-live="polite">
              {suggestions?.suggestions && (
                <div className="suggestions">
                  <h3>Review suggestions</h3>
                  <p className="fine-print">
                    {suggestions.provider || suggestions.mode}
                    {suggestions.model ? ` · ${suggestions.model}` : ""}
                  </p>
                  {Object.entries(suggestions.suggestions)
                    .filter(([name]) =>
                      ["title", "description", "category"].includes(name),
                    )
                    .map(([name, value]) => (
                      <div className="suggestion" key={name}>
                        <b>{label(name)}</b>
                        <p>{value}</p>
                        <button
                          className="button secondary"
                          onClick={() => {
                            revision.current++;
                            markDirty(true);
                            setFields((current) => ({
                              ...current,
                              [name]: value,
                            }));
                            setSuggestions(
                              (current) =>
                                current && {
                                  ...current,
                                  suggestions: Object.fromEntries(
                                    Object.entries(
                                      current.suggestions || {},
                                    ).filter(([field]) => field !== name),
                                  ),
                                },
                            );
                          }}
                        >
                          Use suggestion
                        </button>
                      </div>
                    ))}
                </div>
              )}
            </div>
          </section>
          <section className="panel tips">
            <h3>✦ A good listing goes a long way</h3>
            <p>
              Be honest about condition, use a clear photo, and include the
              details you would want to know.
            </p>
          </section>
        </aside>
      </div>
      <div className="editor-actions">
        <button
          className="button secondary"
          disabled={disabled}
          onClick={saveClick}
        >
          Save changes
        </button>
        {(!item || item.status === "DRAFT") && (
          <button
            className="button primary"
            disabled={disabled || publicationUncertain}
            onClick={publish}
          >
            Review & publish →
          </button>
        )}
      </div>
    </div>
  );
}

export default App;
