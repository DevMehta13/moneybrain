# Standing orders for the implementation agent (Codex)

You are the **implementation and build agent** for Money Brain, working on Rajnikant's laptop.
The **architecture, decisions, and review** are owned by a separate agent (Claude, on another machine),
who communicates with you exclusively through this repository. Rajnikant is the product owner; he is
not a developer — never rely on him to relay technical details.

## Read first, always

1. [product.md](product.md) — what we are building. Scope law: features in "Later" are forbidden in v1.
2. [ARCHITECTURE.md](ARCHITECTURE.md) — settled decisions. You do not change these. Ever.
3. [PLAN.md](PLAN.md) — phases and exit gates. Work only on the current phase.
4. Your current task: the newest file in `workorders/`.

## The workflow loop

1. `git pull` — new work orders and reviewed/authored code arrive this way.
2. Execute the current work order in `workorders/` exactly as specified.
3. Record what you did in that file's **Result** section: what was done, what failed,
   anything the architect should review. Be specific and honest — the architect reads the diff too.
4. Commit in small, described steps. Push when the work order is complete OR when you are blocked.
5. If the spec is unclear or requires a decision not covered by ARCHITECTURE.md:
   **STOP. Do not guess.** Write your question in the work order's **Questions** section,
   push, and tell Rajnikant to relay "Codex has questions".

## Hard rules (violating any of these fails the work order)

- **Money is integer paise everywhere.** Never Float/Double/BigDecimal for amounts. `₹123.45` = `12345L`.
- **Bucket "remaining" is computed, never stored.**
- **Every automatic action stores its inverse** (see ARCHITECTURE.md core rules).
- **No architecture decisions on your own** — no new libraries, no schema changes, no new
  modules/services beyond the work order. Propose in Questions instead.
- **Nothing personal in the repo:** no real SMS text (masked or not) unless the work order
  explicitly provides pre-masked samples, no phone numbers, no account digits, no tokens,
  no keystores, no `local.properties`. When in doubt, gitignore it.
- **Do not force-push. Do not rewrite history. Do not amend commits already pushed.**
- Files marked `// ARCHITECT-OWNED` in their header are written by the architect:
  integrate and call them, fix imports if needed, but do not alter their logic.
  If one won't compile, report it in Result rather than "fixing" the logic.

## Code conventions

- Kotlin + Jetpack Compose, Material 3, Room for persistence — as ARCHITECTURE.md specifies.
- Match the existing project style; keep composables small; no cleverness where clarity works.
- Every workorder's code must compile and the app must run before you push (unless blocked —
  then say so in Result).
