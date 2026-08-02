# 🍩 The Secret Order of the Jelly Donut

**Welcome, Agent.** You cracked the outer seal with the master password `secret`.
You are now a provisional member of the **Secret Order of the Jelly Donut**.

> "In sugar we trust. All others must decrypt." — Grand Glazier, 1847

---

## Your Clearance

This site is a **multi-recipient encryption demo**. A single blob can be
unlocked by _any_ of several passwords ("recipients"). Below are the
classified files and the passwords that open them. Try them out!

| File                     | Passwords that work    | What's inside                           |
| ------------------------ | ---------------------- | --------------------------------------- |
| [`index.md`](index.md)   | `secret`, `sprinkles`  | This very page (you're reading it)      |
| [`recipe.md`](recipe.md) | `sprinkles`, `custard` | The Forbidden Filling Recipe™           |
| [`roster.md`](roster.md) | `admin`, `secret`      | The membership roster (admins only-ish) |
| [`vault.md`](vault.md)   | `sprinkles`            | Vault access. Bakers only.              |

## Cast of Characters

- **You** — password `secret`. You get in the front door. That's it. Enjoy.
- **Chef Bignez** — password `sprinkles`. Sees basically everything.
- **Auditor Crumb** — password `custard`. Only cares about the recipe.
- **The Boss** — password `admin`. Reads the roster, ignores the pastries.

## How Multi-User Works (the funny bit)

We encrypt the _content_ once with a random key (the CEK). Then we lock a
copy of that key in a tiny box for each person. Chef Bignez has a box,
the Auditor has a box, everybody gets a box. Lose your password? No box
for you. Cry into your donut.

---

_If you're reading this, the encryption works. Have a donut. 🍩_
