/**
 * Vendor global declarations for classic-script (non-module) environment.
 *
 * These libraries are loaded via <script> tags from vendor/ and exposed as globals.
 * Declarations here let tsc resolve them in files with // @ts-check.
 */

// --- Keycloak (vendor/keycloak-js/keycloak.min.js) ---

interface KeycloakConfig {
  url?: string;
  realm: string;
  clientId: string;
}

interface KeycloakInitOptions {
  onLoad?: 'login-required' | 'check-sso';
  checkLoginIframe?: boolean;
  pkceMethod?: 'S256' | false;
  useNonce?: boolean;
}

interface KeycloakInstance {
  authenticated?: boolean;
  token?: string;
  idTokenParsed?: Record<string, unknown>;
  realmAccess?: { roles: string[] };
  init(options: KeycloakInitOptions): Promise<boolean>;
  updateToken(minValidity: number): Promise<boolean>;
  hasRealmRole(role: string): boolean;
  login(options?: { redirectUri?: string }): void;
  logout(options?: { redirectUri?: string }): void;
}

declare const Keycloak: {
  new (config: KeycloakConfig): KeycloakInstance;
};

// --- Keycloak ID token payload ---
interface KeycloakUser {
  name?: string;
  preferred_username?: string;
  sub?: string;
  [key: string]: unknown;
}

// --- Bootstrap (vendor/bootstrap/js/bootstrap.bundle.min.js) ---

interface BootstrapOffcanvas {
  show(): void;
  hide(): void;
  dispose(): void;
}

interface BootstrapToast {
  show(): void;
  dispose(): void;
}

declare const bootstrap: {
  Offcanvas: {
    new (element: Element, options?: Record<string, unknown>): BootstrapOffcanvas;
    getInstance(element: Element | null): BootstrapOffcanvas | null;
    getOrCreateInstance(element: Element): BootstrapOffcanvas;
  };
  Toast: {
    new (element: Element, options?: Record<string, unknown>): BootstrapToast;
  };
};

// --- Custom Element consumer interfaces (used by tasks.js / index.js) ---

interface AppErrorBannerElement extends HTMLElement {
  show(message: string): void;
  hide(): void;
}

interface AppConflictBannerElement extends HTMLElement {
  show(): void;
  hide(): void;
}

interface AppDescPopoverElement extends HTMLElement {
  readonly isOpen: boolean | null;
  open(taskId: number, triggerEl: Element, description: string | null): void;
  close(): void;
}

interface AppTaskDrawerElement extends HTMLElement {
  setCurrentUser(userId: number | null): void;
  setUsers(users: TenantUser[]): void;
  open(): void;
  openNew(opts?: Record<string, string>): void;
  openDetail(taskId: number): Promise<void>;
  openEdit(taskId: number): Promise<void>;
}

interface AppPagerElement extends HTMLElement {
  update(opts: {
    currentPage: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
  }): void;
}

interface AppTenantSwitcherElement extends HTMLElement {
  setData(
    tenants: Array<TenantRef> | null,
    activeTenantId: number | null,
  ): void;
}

interface AppTaskRowElement extends HTMLElement {
  setTask(task: Task, currentUserId: number | null, tenantUsers: TenantUser[]): void;
  cancelEdit(): void;
}

