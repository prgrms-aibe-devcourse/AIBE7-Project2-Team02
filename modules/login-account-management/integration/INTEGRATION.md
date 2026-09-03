# Login Account Management Isolation

## Module status

`modules/login-account-management` is an archived standalone prototype. The production source of truth is `src/main/java/org/example/matcheat/domain/account`.

Do not include this directory from the root `settings.gradle`, add it as a root project dependency, copy its Spring beans into the production application, or merge its security snippet into `SecurityConfig`.

## Isolated build

From the repository root on Windows:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-account-module"
.\gradlew.bat -p modules\login-account-management clean test
```

The dedicated cache path avoids contention with another Gradle process using the default user cache.

## Production boundary

- Root account source: `src/main/java/org/example/matcheat/domain/account`
- Root security chain: `src/main/java/org/example/matcheat/config/SecurityConfig.java`
- Root account tests: `src/test/java/org/example/matcheat/domain/account`
- Root security tests: `src/test/java/org/example/matcheat/config/SecurityConfigMvcTest.java`

The files under `integration` are retained as historical design references. Their Gradle and security snippets are deliberately disabled.

## JWT secret generation

Generate at least 32 random bytes and store their Base64 representation as `JWT_SECRET`. Do not use a memorable sentence, example value, or repository default.

## Security boundaries

- Browser authentication uses `Authorization: Bearer`; no authentication cookie is created.
- HTML routes remain public because browser navigation cannot attach a Bearer header.
- Protected access is enforced at `/api/**`.
- The access token is stored under `sessionStorage['matcheat.accessToken']`.
- Access tokens expire after one hour by default and cannot be revoked immediately in this module version.
- A suspended or withdrawn user's already-issued token can remain usable until expiration. Refresh tokens and token-version checks are deferred.

## Historical database ownership

The production root account domain owns `users` and `seller_profiles`. This legacy module must never manage the production schema.

Flyway adoption remains a team decision. Until then, `auth-schema.sql` is the authoritative schema contract and the module does not force `ddl-auto=update`.

## Future extension points

- `ProfileUseCase`: current-user query, name update, withdrawal
- `SellerApplicationUseCase`: seller application
- `AdminAccountManagementUseCase`: user status and seller review

Other administrator domains such as products, orders, reports, inquiries, and contracts remain outside this module.
