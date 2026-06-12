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
  init(options: KeycloakInitOptions): Promise<boolean>;
  updateToken(minValidity: number): Promise<boolean>;
  login(options?: { redirectUri?: string }): void;
  logout(options?: { redirectUri?: string }): void;
}

declare const Keycloak: {
  new (config: KeycloakConfig): KeycloakInstance;
};

// --- Bootstrap (vendor/bootstrap/js/bootstrap.bundle.min.js) ---
// Used in component files (none of which have // @ts-check).
declare const bootstrap: Record<string, unknown>;
