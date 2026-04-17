# ShopTalk — Session 04 Notes
**Date:** 17 April 2026
**Duration:** Full day session
**Topic:** React Frontend — TypeScript, Components, State, Routing, CORS

---

## What we built today

Connected a React + TypeScript frontend to the Spring Boot Auth Service.
Full auth flow working in a real browser — register, login, protected dashboard, logout.

```
shoptalk-ui (port 5173)
├── Register page  → POST /api/auth/register
├── Login page     → POST /api/auth/login → stores JWT in sessionStorage
├── Dashboard      → protected route, shows JWT token
└── Sign out       → POST /api/auth/logout → clears sessionStorage
```

---

## 1. Core React concepts learned

### What is React?

React is a JavaScript library for building UIs. Core idea:

```
UI = f(data)
```

When data changes — React automatically updates only the affected parts of the page.
You never manually touch the HTML. React does it for you.

### What is a component?

A component is a TypeScript function that returns JSX (HTML-like syntax):

```typescript
function WelcomeMessage() {
    return <h1>Welcome to ShopTalk</h1>;
}
```

That is a complete, valid React component. A function. Returns UI.

### What is JSX?

JSX looks like HTML but lives inside TypeScript files. React converts it to real browser DOM elements.

```typescript
// JSX — not real HTML
<div className="auth-card">    // className not class
    <h1>{title}</h1>           // {} for JavaScript expressions
    {error && <p>{error}</p>}  // conditional rendering
</div>
```

### What is state — `useState`?

State is data that belongs to a component. When state changes, React re-renders automatically:

```typescript
const [email, setEmail] = useState('');
//     ^^^^^  ^^^^^^^^^  ^^^^^^^^^^^
//     read   update      initial value

// Reading
console.log(email);        // current value

// Updating — triggers re-render
setEmail('akash@gmail.com');
```

### What is `useEffect`?

Runs code AFTER the component renders. Safe place for side effects like navigation:

```typescript
useEffect(() => {
    if (!token) navigate('/login');
}, [token, navigate]); // dependency array — re-run when these change
```

### What is `useNavigate`?

Programmatic navigation between pages:

```typescript
const navigate = useNavigate();
navigate('/dashboard');  // go to dashboard
navigate('/login');      // go to login
```

### Controlled inputs

React controls the input value — every keystroke updates state:

```typescript
<input
    value={email}                          // React controls displayed value
    onChange={e => setEmail(e.target.value)} // every keystroke updates state
/>
```

### Conditional rendering

```typescript
{error && <div className="error-message">{error}</div>}
// if error is non-empty → show div
// if error is empty string → show nothing

{loading ? 'Signing in...' : 'Sign in'}
// ternary — same as Java's condition ? a : b
```

---

## 2. TypeScript concepts learned

### Interfaces — like Java DTOs

```typescript
export interface LoginRequest {
    email: string;
    password: string;
}

export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    userId: string;
    role: string;
}
```

### Union types

```typescript
role: 'BUYER' | 'SELLER'  // only these two values allowed
```

### Optional chaining

```typescript
err.response?.data?.message
// if err.response is null — don't crash, return undefined
// same as Java's null checks but cleaner
```

### async/await

```typescript
const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
        const response = await login({ email, password }); // wait for HTTP call
        navigate('/dashboard');
    } catch (err: any) {
        setError(err.response?.data?.message || 'Login failed');
    }
};
```

---

## 3. Project structure

```
shoptalk-ui/
├── src/
│   ├── pages/
│   │   ├── RegisterPage.tsx    ← register form
│   │   ├── LoginPage.tsx       ← login form
│   │   └── DashboardPage.tsx   ← protected dashboard
│   ├── services/
│   │   └── authService.ts      ← HTTP calls to Spring Boot
│   ├── styles/
│   │   └── auth.css            ← all styling
│   └── main.tsx                ← app entry, routing
├── package.json
└── vite.config.ts
```

---

## 4. `authService.ts`

Handles all HTTP calls to Auth Service — equivalent of Spring Boot's service layer:

```typescript
import axios from 'axios';

const AUTH_API = 'http://localhost:8082/api/auth';

export interface RegisterRequest {
    email: string;
    password: string;
    role: 'BUYER' | 'SELLER';
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    userId: string;
    role: string;
}

export const register = async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await axios.post(`${AUTH_API}/register`, data);
    return response.data;
};

export const login = async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await axios.post(`${AUTH_API}/login`, data);
    return response.data;
};

export const logout = async (accessToken: string): Promise<void> => {
    await axios.post(`${AUTH_API}/logout`, {}, {
        headers: { Authorization: `Bearer ${accessToken}` }
    });
};
```

**What axios does:**
- Automatically sets `Content-Type: application/json`
- Parses JSON response automatically
- Throws on non-2xx status codes (caught in catch block)

---

## 5. Routing — `main.tsx`

React Router maps URLs to components:

```typescript
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Navigate to="/login" />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route path="/dashboard" element={<DashboardPage />} />
            </Routes>
        </BrowserRouter>
    </StrictMode>
);
```

```
localhost:5173/         → redirects to /login
localhost:5173/login    → LoginPage component
localhost:5173/register → RegisterPage component
localhost:5173/dashboard → DashboardPage component
```

---

## 6. Token storage — `sessionStorage`

After login, tokens stored in sessionStorage:

```typescript
sessionStorage.setItem('accessToken', response.accessToken);
sessionStorage.setItem('refreshToken', response.refreshToken);
sessionStorage.setItem('userId', response.userId);
sessionStorage.setItem('role', response.role);

// Read
const token = sessionStorage.getItem('accessToken');

// Clear all (logout)
sessionStorage.clear();
```

**Why sessionStorage not localStorage:**
- `localStorage` — persists forever, even after browser closes
- `sessionStorage` — clears when tab closes (more secure)
- Token disappears automatically when user closes the browser

---

## 7. Protected route pattern

DashboardPage redirects to login if no token:

```typescript
const token = sessionStorage.getItem('accessToken');

// useEffect — runs after render, safe to navigate here
useEffect(() => {
    if (!token) navigate('/login');
}, [token, navigate]);

// Prevent rendering dashboard content while redirecting
if (!token) return null;
```

**Why `useEffect` not direct `if/navigate`:**
`useNavigate` cannot be called during initial render — React throws a warning.
`useEffect` runs after render — safe place for navigation side effects.

---

## 8. CORS — the browser security issue

**What is CORS:**
Browser blocks HTTP requests between different origins:
```
React  : http://localhost:5173  ← origin 1
Spring : http://localhost:8082  ← origin 2 (different port = different origin)
```

Browser sends an `OPTIONS` preflight request first to check if CORS is allowed.
Without CORS config — Spring returns `403 Forbidden` on OPTIONS → request blocked.

**Error seen:**
```
Access to XMLHttpRequest blocked by CORS policy:
No 'Access-Control-Allow-Origin' header is present
OPTIONS http://localhost:8082/api/auth/register → 403 Forbidden
```

**Fix — `SecurityConfig.java` in auth-service:**

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().permitAll()
        );
    return http.build();
}

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:5173"));
    config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

**Key points:**
- `OPTIONS` must be explicitly permitted — Spring Security blocks it by default
- `setAllowedOrigins` — only allow React dev server
- `setAllowCredentials(true)` — allow Authorization header cross-origin

---

## 9. Full test results

| Action | Result |
|---|---|
| Visit `/` | Redirects to `/login` |
| Register new user | 201, redirects to login after 2s |
| Register duplicate email | Red error: "Email already exists" |
| Login with correct credentials | 200, redirects to dashboard |
| Login with wrong password | Red error: "Invalid email or password" |
| Dashboard shows JWT token | JWT displayed, role badge shown |
| Visit `/dashboard` without login | Redirects to `/login` |
| Sign out | Clears sessionStorage, redirects to login |

---

## 10. Issues encountered and fixed

| Issue | Cause | Fix |
|---|---|---|
| `%0A` in URL | Copied URL with newline from chat | Retyped URL manually in Postman |
| Registration failed from browser | CORS blocked by browser | Added CorsConfigurationSource bean |
| OPTIONS request → 403 | Spring Security blocked preflight | Added `HttpMethod.OPTIONS` permitAll |
| Dashboard not redirecting | `navigate()` called during render | Moved to `useEffect` |
| Login calling `register` | Wrong function imported | Changed to `login` from authService |
| Missing state variables | `setLoading`, `setError` used without `useState` | Added missing state declarations |

---

## 11. Key concepts to remember

| Concept | One-line summary |
|---|---|
| Component | TypeScript function that returns JSX |
| useState | Watched variable — changes trigger re-render |
| useEffect | Runs after render — safe for side effects |
| useNavigate | Programmatic page navigation |
| Controlled input | React controls input value via state |
| sessionStorage | Tab-scoped token storage — clears on close |
| CORS | Browser blocks cross-origin requests — Spring must explicitly allow |
| OPTIONS preflight | Browser checks CORS before every POST — must return 200 |
| axios | HTTP client — auto JSON, throws on errors |
| Interface | TypeScript DTO — defines shape of an object |

---

## Repositories updated this session

```
auth-service  → CORS config added to SecurityConfig.java
shoptalk-ui   → New repo created, full frontend pushed
```

---

## What's coming in Session 05

```
├── Product Service — Spring Boot microservice
├── Product entity, repository, service, controller
├── Elasticsearch integration — product search
├── Image upload with MinIO
└── Products page in React frontend
```

---

*ShopTalk Learning Journal — Session 04 complete*
